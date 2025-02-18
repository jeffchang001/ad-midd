package com.sogo.ad.midd.controller;

import com.sogo.ad.midd.model.dto.ADSyncDto;
import com.sogo.ad.midd.service.ADLDAPSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.naming.NamingException;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "AD Sync", description = "提供 AD 同步功能的 API")
public class ADSyncController {

    private final RestTemplate restTemplate;
    private final ADLDAPSyncService adldapSyncService;

    @Value("${ad.sync.base-url}")
    private String baseUrl;

    @Value("${ad.sync.token}")
    private String token;

    @PostMapping("/process-ad-data")
    @Operation(summary = "處理 AD 同步數據", description = "從伺服器獲取 AD 同步數據並進行處理")
    @ApiResponse(responseCode = "200", description = "同步成功完成")
    @ApiResponse(responseCode = "204", description = "沒有數據需要同步")
    @ApiResponse(responseCode = "500", description = "同步過程中發生錯誤")
    public ResponseEntity<String> syncADData() {
        try {
            List<ADSyncDto> syncDataList = fetchADSyncData();
            if (syncDataList == null || syncDataList.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            log.info("獲取到 {} 條 AD 同步數據", syncDataList.size());
            processADSyncData(syncDataList);
            return ResponseEntity.ok("同步成功完成");
        } catch (Exception e) {
            log.error("AD 同步過程中發生錯誤", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("同步過程中發生錯誤: " + e.getMessage());
        }
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
                log.info("處理同步數據, 員工編號: {}, 組織名稱: {}",
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