package com.sogo.ad.midd.controller;

import java.util.List;

import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.sogo.ad.midd.model.dto.ADSyncDto;
import com.sogo.ad.midd.service.ADLDAPSyncService;

@RestController
public class ADSyncController {

    private static final Logger logger = LoggerFactory.getLogger(ADSyncController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ADLDAPSyncService adldapSyncService;

    @Value("${ad.sync.base-url}")
    private String baseUrl;

    @Value("${ad.sync.token}")
    private String token;

    @GetMapping("/process-ad-data")
    public ResponseEntity<String> syncADData() {
        String url = baseUrl + "/api/v1/ad-sync-data";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<ADSyncDto>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<ADSyncDto>>() {
                });

        List<ADSyncDto> syncDataList = response.getBody();
        if (syncDataList != null) {
            for (ADSyncDto adSyncDto : syncDataList) {

                logger.info("adSyncDto=" + adSyncDto.getOrgHierarchyDto().get(0).getOrgName());

                try {
                    adldapSyncService.syncEmployeeToLDAP(adSyncDto);

                    // TODO: 尚未處理組織
                    // adldapSyncService.syncOrganizationToLDAP(adSyncDto);
                } catch (NamingException e) {
                    logger.error("LDAP操作錯誤,員工編號: " + adSyncDto.getEmployeeNo(), e);
                    // 可以選擇繼續處理下一筆資料,或者拋出例外中斷整個同步過程
                    // throw new RuntimeException("LDAP同步失敗", e);
                } catch (DataAccessException e) {
                    logger.error("資料訪問錯誤,員工編號: " + adSyncDto.getEmployeeNo(), e);
                    // 同上,可以選擇繼續或中斷
                } catch (Exception e) {
                    logger.error("未預期的錯誤,員工編號: " + adSyncDto.getEmployeeNo(), e);
                    // 同上,可以選擇繼續或中斷
                }
                // adldapSyncService.syncOrganizationToLDAP(adSyncDto);
            }
        }

        return ResponseEntity.ok("Sync completed successfully");
    }
}