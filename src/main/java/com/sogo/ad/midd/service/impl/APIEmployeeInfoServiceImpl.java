package com.sogo.ad.midd.service.impl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sogo.ad.midd.model.dto.APIEmployeeInfoDto;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.repository.APIEmployeeInfoRepository;
import com.sogo.ad.midd.service.APIEmployeeInfoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class APIEmployeeInfoServiceImpl implements APIEmployeeInfoService {

    @Autowired
	private APIEmployeeInfoRepository employeeInfoRepo;
    
    @Value("${radar.api.server.uri}")
	private String radarAPIServerURI;

	@Value("${radar.api.token}")
	private String radarAPIToken;
	
	@Autowired
    private RestTemplate restTemplate;

    @Override
    @Transactional
	public void initEmployeeInfo(ResponseEntity<String> response) throws Exception {
		// 解析 JSON
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		APIEmployeeInfoDto apiEmployeeInfoResponse = objectMapper.readValue(response.getBody(),
				APIEmployeeInfoDto.class);

		List<APIEmployeeInfo> newEmployeeInfoList = apiEmployeeInfoResponse.getResult();
		// 儲存employeeInfo資料
		employeeInfoRepo.truncateTable();
		employeeInfoRepo.saveAll(newEmployeeInfoList);
	}

    @Override
    @Transactional
	public void addEmployeeInfoEmailToRadar(APIEmployeeInfo employeeInfo) throws Exception {
		log.info("開始更新員工 {} 的郵件地址與分機號碼到 Radar", employeeInfo.getEmployeeNo());
        
        try {
            // 準備請求頭
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-api-token", radarAPIToken);
            
            // 準備請求體
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("employeeNo", employeeInfo.getEmployeeNo());
            requestBody.put("eMail", employeeInfo.getEmailAddress());
            requestBody.put("extNo", employeeInfo.getExtNo());
            
            // 建立 HTTP 實體
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            // 組合 API URL
            String apiUrl = radarAPIServerURI + "/api/ZZApi/ZZUpdateEmpEMailExtNo";
            
            // 使用注入的 RestTemplate 發送 POST 請求
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
            
            // 處理回應
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("成功更新員工 {} 的郵件地址與分機號碼", employeeInfo.getEmployeeNo());
            } else {
                log.error("更新員工 {} 的郵件地址與分機號碼失敗，HTTP狀態碼: {}", 
                        employeeInfo.getEmployeeNo(), response.getStatusCode());
                throw new Exception("更新員工郵件地址與分機號碼失敗，HTTP狀態碼: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("更新員工 {} 的郵件地址與分機號碼時發生錯誤: {}", 
                    employeeInfo.getEmployeeNo(), e.getMessage(), e);
            throw e;
        }
	}

    @Override
    public APIEmployeeInfo findByEmployeeNo(String employeeNo) throws Exception {
        return employeeInfoRepo.findByEmployeeNo(employeeNo);
    }

}
