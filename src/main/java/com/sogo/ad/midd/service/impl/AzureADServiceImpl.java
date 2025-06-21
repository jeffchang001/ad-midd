package com.sogo.ad.midd.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.model.entity.APIEmployeeInfoActionLog;
import com.sogo.ad.midd.repository.APIEmployeeInfoActionLogRepository;
import com.sogo.ad.midd.repository.APIEmployeeInfoRepository;
import com.sogo.ad.midd.service.AzureADService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AzureADServiceImpl implements AzureADService {

    @Value("${azure.ad.tenant-id}")
    private String tenantId;

    @Value("${azure.ad.client-id}")
    private String clientId;

    @Value("${azure.ad.client-secret}")
    private String clientSecret;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private APIEmployeeInfoServiceImpl apiEmployeeInfoService;

    @Autowired
    private APIEmployeeInfoActionLogRepository employeeInfoActionLogRepository;

    @Autowired
    private APIEmployeeInfoRepository employeeInfoRepository;

    @Override
    public void enableAADE1Account(String employeeNo, String baseDate) throws Exception {
        List<APIEmployeeInfoActionLog> employeeInfoActionLogList = null;
        if ("".equals(employeeNo) || employeeNo == null) {
            employeeInfoActionLogList = employeeInfoActionLogRepository
                    .findByAndActionAndCreatedDate("C", baseDate);
        } else {
            employeeInfoActionLogList = employeeInfoActionLogRepository
                    .findByEmployeeNoAndActionAndCreatedDate(employeeNo, "C", baseDate);
        }
        for (APIEmployeeInfoActionLog actionLog : employeeInfoActionLogList) {
            APIEmployeeInfo employeeInfo = employeeInfoRepository.findByEmployeeNo(actionLog.getEmployeeNo());
            enableAADE1AccountProcessor(employeeInfo.getEmailAddress(), employeeInfo.getIdNoSuffix());
        }
    }

    @Override
    public void disableAADE1Account(String employeeNo, String baseDate) throws Exception {
        List<APIEmployeeInfoActionLog> employeeInfoActionLogList = null;
        if ("".equals(employeeNo) || employeeNo == null) {
            employeeInfoActionLogList = employeeInfoActionLogRepository
                    .findByAndActionAndCreatedDate("D", baseDate);
        } else {
            employeeInfoActionLogList = employeeInfoActionLogRepository
                    .findByEmployeeNoAndActionAndCreatedDate(employeeNo, "D", baseDate);
        }
        for (APIEmployeeInfoActionLog actionLog : employeeInfoActionLogList) {
            APIEmployeeInfo employeeInfo = employeeInfoRepository.findByEmployeeNo(actionLog.getEmployeeNo());
            disableAADE1AccountProcessor(employeeInfo.getEmailAddress());
        }
    }

    @Override
    public void enableAADE1AccountProcessor(String employeeEmail, String idNoSuffix) throws Exception {
        String userId = getUserIdByEmail(employeeEmail);
        String skuId = getSkuId();
        setUsageLocation(userId, "TW");
        assignE1License(userId, skuId, "C");
        resetUserPassword(userId, "Sogo$" + idNoSuffix, true);
    }

    @Override
    public void disableAADE1AccountProcessor(String employeeEmail) throws Exception {
        String userId = getUserIdByEmail(employeeEmail);
        String skuId = getSkuId();
        setUsageLocation(userId, "TW");
        assignE1License(userId, skuId, "D");
    }

    @Override
    public void deleteAADE1AllAccountProcessor() throws Exception {
        log.info("開始執行刪除所有 E1 帳戶的程序");
        int successCount = 0;
        int failCount = 0;

        List<APIEmployeeInfo> aadAccountsList = employeeInfoRepository.findAll();
        log.info("找到 {} 個待處理的帳戶", aadAccountsList.size());
        
        for (APIEmployeeInfo employeeInfo : aadAccountsList) {
            try {
                log.info("開始處理用戶: {}, 電子郵件: {}", employeeInfo.getEmployeeNo(), employeeInfo.getEmailAddress());
                String userId = getUserIdByEmail(employeeInfo.getEmailAddress());
                
                if (userId == null || userId.isEmpty()) {
                    log.warn("無法取得用戶 {} 的 ID，可能不存在於 Azure AD", employeeInfo.getEmailAddress());
                    failCount++;
                    continue;
                }
                
                log.debug("設定用戶 {} 的使用地點", userId);
                setUsageLocation(userId, "TW");
                
                log.debug("執行刪除用戶 {}", userId);
                deleteAADE1Account(userId);
                
                log.info("成功刪除用戶: {}, 電子郵件: {}", employeeInfo.getEmployeeNo(), employeeInfo.getEmailAddress());
                successCount++;
            } catch (Exception e) {
                log.error("處理用戶 {} ({}) 時發生錯誤: {}", 
                         employeeInfo.getEmployeeNo(), 
                         employeeInfo.getEmailAddress(), 
                         e.getMessage(), e);
                failCount++;
            }
        }
        
        log.info("刪除 E1 帳戶程序完成。成功: {}, 失敗: {}, 總計: {}", 
                successCount, failCount, aadAccountsList.size());
    }
    
    private void deleteAADE1Account(String userId) throws Exception {
        log.info("開始刪除用戶 ID: {}", userId);
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            log.debug("發送刪除請求至 Azure AD Graph API: {}", url);
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    Void.class);
            
            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.info("成功刪除使用者 {}", userId);
            } else {
                log.error("刪除使用者 {} 失敗: {}", userId, response.getStatusCode());
                throw new Exception("刪除使用者失敗，HTTP狀態碼: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("刪除使用者 {} 時發生 HTTP 錯誤: {} - {}", 
                    userId, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("使用者 {} 可能不存在或已被刪除", userId);
            }
            throw e;
        } catch (Exception e) {
            log.error("刪除使用者 {} 時發生未預期錯誤: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private String getAccessToken() throws Exception {
        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("scope", "https://graph.microsoft.com/.default");
        map.add("client_secret", clientSecret);
        map.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(response.getBody());
        return rootNode.path("access_token").asText();
    }

    private String getUserIdByEmail(String userEmail) throws Exception {
        log.info("嘗試通過電子郵件 {} 獲取用戶 ID", userEmail);
        if (userEmail == null || userEmail.trim().isEmpty()) {
            log.error("電子郵件地址為空，無法獲取用戶 ID");
            return null;
        }
        
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userEmail;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);
            log.debug("發送請求至 Azure AD Graph API: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String jsonResponse = response.getBody();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(jsonResponse);
                String userId = rootNode.path("id").asText();
                log.info("成功獲取用戶 ID: {} 對應電子郵件: {}", userId, userEmail);
                return userId;
            } else {
                log.error("獲取用戶 ID 失敗，HTTP狀態碼: {}", response.getStatusCode());
                return null;
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("用戶 {} 在 Azure AD 中不存在", userEmail);
            } else {
                log.error("獲取電子郵件 {} 的用戶 ID 時發生 HTTP 錯誤: {} - {}", 
                        userEmail, e.getStatusCode(), e.getResponseBodyAsString());
            }
            return null;
        } catch (Exception e) {
            log.error("獲取電子郵件 {} 的用戶 ID 時發生未預期錯誤: {}", userEmail, e.getMessage(), e);
            return null;
        }
    }

    private String getSkuId() {
        String skuId = null;
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/subscribedSkus";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            String jsonResponse = response.getBody();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            JsonNode skus = rootNode.path("value");
            for (JsonNode sku : skus) {
                log.info("SKU ID: " + sku.path("skuId").asText() +
                        ", SKU Name: " + sku.path("skuPartNumber").asText());
                skuId = sku.path("skuId").asText();
            }
        } catch (Exception e) {
            log.info("Error getting SKU ID: {}", e.getMessage());
        }
        return skuId;
    }

    private void setUsageLocation(String userId, String usageLocation) {
        log.info("開始設置用戶 {} 的使用地點為 {}", userId, usageLocation);
        if (userId == null || userId.trim().isEmpty()) {
            log.error("用戶 ID 為空，無法設置使用地點");
            return;
        }
        
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> bodyMap = new HashMap<>();
            bodyMap.put("usageLocation", usageLocation);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(bodyMap, headers);
            log.debug("發送 PATCH 請求至 Azure AD Graph API: {}", url);

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    entity,
                    Void.class);

            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.info("成功設置用戶 {} 的使用地點為 {}", userId, usageLocation);
            } else {
                log.error("設置使用地點失敗，HTTP狀態碼: {}", response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("用戶 {} 在 Azure AD 中不存在，無法設置使用地點", userId);
            } else {
                log.error("設置用戶 {} 的使用地點時發生 HTTP 錯誤: {} - {}", 
                        userId, e.getStatusCode(), e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.error("設置用戶 {} 的使用地點時發生未預期錯誤: {}", userId, e.getMessage(), e);
        }
    }

    private void assignE1License(String userId, String skuID, String action) throws Exception {
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userId + "/assignLicense";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> bodyMap = new HashMap<>();
            List<Map<String, String>> addLicenses = new ArrayList<>();
            List<String> removeLicenses = new ArrayList<>();

            if ("C".equals(action)) {
                Map<String, String> license = new HashMap<>();
                license.put("skuId", skuID);
                addLicenses.add(license);
                resetUserPassword(userId, "P@ssw0rd", true);
            } else if ("D".equals(action)) {
                removeLicenses.add(skuID);
            } else {
                log.info("Invalid action");
                return;
            }

            bodyMap.put("addLicenses", addLicenses);
            bodyMap.put("removeLicenses", removeLicenses);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("E1 license {} successfully for user {}",
                        "C".equals(action) ? "assigned" : "removed", userId);
            } else {
                log.info("Error: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.info("Error {} E1 license for user {}: {}",
                    "C".equals(action) ? "assigning" : "removing", userId, e.getMessage());
        }
    }

    private void resetUserPassword(String userId, String newPassword, boolean forceChangePasswordNextSignIn) {
        try {
            String accessToken = getAccessToken();
            // 注意: 這裡不再使用 /resetPassword 端點
            String url = "https://graph.microsoft.com/v1.0/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 創建密碼配置對象
            Map<String, Object> passwordProfile = new HashMap<>();
            passwordProfile.put("password", newPassword);
            passwordProfile.put("forceChangePasswordNextSignIn", forceChangePasswordNextSignIn);

            // 創建主體對象
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("passwordProfile", passwordProfile);
            bodyMap.put("accountEnabled", Boolean.TRUE);  // accountEnabled 應在外層，不是 passwordProfile 內

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(bodyMap, headers);

            // 使用 PATCH 方法更新用戶
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.info("成功重設用戶 {} 的密碼", userId);
            } else {
                log.error("重設密碼失敗: {}", response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("重設用戶 {} 密碼時發生錯誤: {} - {}",
                    userId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("重設用戶 {} 密碼時發生錯誤: {}", userId, e.getMessage());
        }
    }

    @Override
    public void syncEmployeeDataToRadar(String employeeNo, String baseDate) throws Exception {
        List<APIEmployeeInfoActionLog> employeeInfoActionLogList = null;
        if ("".equals(employeeNo) || employeeNo == null) {
            employeeInfoActionLogList = employeeInfoActionLogRepository
                    .findByAndActionAndCreatedDate("C", baseDate);
        } else {
            employeeInfoActionLogList = employeeInfoActionLogRepository
                    .findByEmployeeNoAndActionAndCreatedDate(employeeNo, "C", baseDate);
        }
        for (APIEmployeeInfoActionLog actionLog : employeeInfoActionLogList) {
            APIEmployeeInfo employeeInfo = employeeInfoRepository.findByEmployeeNo(actionLog.getEmployeeNo());
            // 將員工資訊同步至 Radar
            apiEmployeeInfoService.addEmployeeInfoEmailToRadar(employeeInfo);
        }
    }

}
