package com.sogo.ad.midd.controller;

import java.util.List;

import javax.naming.NamingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.sogo.ad.midd.model.dto.ADSyncDto;
import com.sogo.ad.midd.service.ADLDAPSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ADSyncController {

    private final RestTemplate restTemplate;
    private final ADLDAPSyncService adldapSyncService;

    @Value("${ad.sync.base-url}")
    private String baseUrl;

    @Value("${ad.sync.token}")
    private String token;

    @GetMapping("/process-ad-data")
    public ResponseEntity<String> syncADData() {
        List<ADSyncDto> syncDataList = fetchADSyncData();
        if (syncDataList == null || syncDataList.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        processADSyncData(syncDataList);
        return ResponseEntity.ok("同步成功完成");
    }

    private List<ADSyncDto> fetchADSyncData() {
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

        return response.getBody();
    }

    private void processADSyncData(List<ADSyncDto> syncDataList) {
        for (ADSyncDto adSyncDto : syncDataList) {
            try {
                log.info("處理同步數據,員工編號: {}, 組織名稱: {}",
                        adSyncDto.getEmployeeNo(),
                        adSyncDto.getOrgHierarchyDto().get(0).getOrgName());

                adldapSyncService.syncEmployeeToLDAP(adSyncDto);

                // TODO: 處理組織同步
                // adldapSyncService.syncOrganizationToLDAP(adSyncDto);
            } catch (NamingException e) {
                handleSyncException("LDAP操作錯誤", adSyncDto, e);
            } catch (Exception e) {
                handleSyncException("未預期的錯誤", adSyncDto, e);
            }
        }
    }

    private void handleSyncException(String errorType, ADSyncDto adSyncDto, Exception e) {
        log.error("{}, 員工編號: {}", errorType, adSyncDto.getEmployeeNo(), e);
        // 可以選擇在這裡添加更多的錯誤處理邏輯，例如發送通知或記錄到數據庫
    }
}