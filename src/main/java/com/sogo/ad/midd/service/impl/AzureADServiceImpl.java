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
        String accessToken = getAccessToken();
        String url = "https://graph.microsoft.com/v1.0/users/" + userEmail;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String jsonResponse = response.getBody();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(jsonResponse);
                return rootNode.path("id").asText();
            } else {
                log.info("Error fetching user ID: " + response.getStatusCode());
                return null;
            }
        } catch (HttpClientErrorException e) {
            log.info("Error fetching user ID for email {}: {}", userEmail, e.getMessage());
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
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> bodyMap = new HashMap<>();
            bodyMap.put("usageLocation", usageLocation);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    entity,
                    Void.class);

            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.info("Usage location set to {} for user {}", usageLocation, userId);
            } else {
                log.info("Error setting usage location: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.info("Error setting usage location for user {}: {}", userId, e.getMessage());
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
            passwordProfile.put("accountEnabled", Boolean.TRUE);
            passwordProfile.put("password", newPassword);
            passwordProfile.put("forceChangePasswordNextSignIn", forceChangePasswordNextSignIn);

            // 創建主體對象
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("passwordProfile", passwordProfile);

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

}
