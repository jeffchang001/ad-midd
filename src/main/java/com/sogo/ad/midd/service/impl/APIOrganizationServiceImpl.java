package com.sogo.ad.midd.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sogo.ad.midd.model.dto.APIOrganizationDto;
import com.sogo.ad.midd.model.entity.APIOrganization;
import com.sogo.ad.midd.model.entity.APIOrganizationActionLog;
import com.sogo.ad.midd.repository.APIOrganizationActionLogRepository;
import com.sogo.ad.midd.repository.APIOrganizationRepository;
import com.sogo.ad.midd.service.APIOrganizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class APIOrganizationServiceImpl implements APIOrganizationService {

    @Autowired
    APIOrganizationRepository apiOrganizationRepository;

    @Autowired
    APIOrganizationActionLogRepository apiOrganizationActionLogRepository;
    
    private final ObjectMapper objectMapper;

    @Value("${radar.api.server.uri}")
    private String radarAPIServerURI;

    @Value("${radar.api.token}")
    private String radarAPIToken;

    @Override
    @Transactional
    public void initOrganizationInfo(ResponseEntity<String> response) throws Exception {
        try {
            log.info("開始初始化組織資訊");
            
            APIOrganizationDto apiOrganizationDto = objectMapper.readValue(response.getBody(), APIOrganizationDto.class);
            
            if (apiOrganizationDto.getHttpStatusCode() != 200) {
                log.error("初始化組織資訊失敗，API回應錯誤: {}", apiOrganizationDto.getErrorCode());
                throw new Exception("初始化組織資訊失敗，API回應錯誤: " + apiOrganizationDto.getErrorCode());
            }
            
            List<APIOrganization> organizations = apiOrganizationDto.getResult();
            
            if (organizations == null || organizations.isEmpty()) {
                log.info("沒有組織資訊需要初始化");
                return;
            }
            
            log.info("共有 {} 筆組織資訊需要初始化", organizations.size());
            
            for (APIOrganization organization : organizations) {
                Optional<APIOrganization> existingOrg = apiOrganizationRepository.findByOrgCode(organization.getOrgCode());
                
                if (existingOrg.isPresent()) {
                    // 更新組織資訊
                    APIOrganization oldOrg = existingOrg.get();
                    
                    // 儲存更新的組織資訊
                    organization.setId(oldOrg.getId());
                    apiOrganizationRepository.save(organization);
                    
                } else {
                    // 新增組織資訊
                    organization.setStatus("A");
                    apiOrganizationRepository.save(organization);
                    
                    // 記錄新增組織資訊
                    APIOrganizationActionLog log = new APIOrganizationActionLog(
                            organization.getOrgCode(), "C", "ALL", "", "新增組織資訊");
                    apiOrganizationActionLogRepository.save(log);
                }
            }
            
            log.info("組織資訊初始化完成");
        } catch (Exception e) {
            log.error("初始化組織資訊時發生錯誤", e);
            throw e;
        }
    }

    @Override
    public APIOrganization findByOrgCode(String orgCode) throws Exception {
        log.info("查詢組織資訊，組織代碼: {}", orgCode);
        Optional<APIOrganization> organization = apiOrganizationRepository.findByOrgCode(orgCode);
        
        if (organization.isPresent()) {
            return organization.get();
        } else {
            log.error("找不到組織資訊，組織代碼: {}", orgCode);
            throw new Exception("找不到組織資訊");
        }
    }
} 