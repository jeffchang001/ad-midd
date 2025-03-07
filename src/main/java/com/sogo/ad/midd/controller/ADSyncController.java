package com.sogo.ad.midd.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.sogo.ad.midd.model.dto.ADEmployeeSyncDto;
import com.sogo.ad.midd.model.dto.ADOrganizationSyncDto;
import com.sogo.ad.midd.service.ADLDAPSyncService;
import com.sogo.ad.midd.service.AzureADService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "AD Sync", description = "提供 AD 同步功能的 API")
public class ADSyncController {

    private final RestTemplate restTemplate;
    private final ADLDAPSyncService adldapSyncService;
    private final AzureADService azureADService;

    @Value("${ad.sync.base-url}")
    private String baseUrl;

    @Value("${ad.sync.token}")
    private String token;

    @PostMapping("/process-ad-employee-data")
    @Operation(summary = "處理 AD 員工同步數據", description = "從伺服器獲取 AD 員工同步數據並進行處理")
    @ApiResponse(responseCode = "200", description = "同步員工成功完成")
    @ApiResponse(responseCode = "204", description = "沒有員工數據需要同步")
    @ApiResponse(responseCode = "500", description = "同步過程中發生錯誤")
    public ResponseEntity<String> syncADEmployeeData(
            @Parameter(description = "基準日期：日期之後的資料", schema = @Schema(type = "string", format = "date", example = "2025-02-26")) @RequestParam(name = "base-date", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        try {
            List<ADEmployeeSyncDto> syncDataList = fetchADSyncData(baseDate);
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

    private List<ADEmployeeSyncDto> fetchADSyncData(LocalDate baseDate) {
        String apiUrl = baseUrl + "/api/v1/ad-employee-sync-data";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);

        Map<String, String> params = new HashMap<>();
        params.put("base-date", baseDate != null ? baseDate.toString().trim() : "");

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl);
        if (params != null) {
            params.forEach(builder::queryParam);
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<ADEmployeeSyncDto>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<ADEmployeeSyncDto>>() {
                });

        return response.getBody();
    }

    private void processADSyncData(List<ADEmployeeSyncDto> syncDataList) {

        // 處理員工同步
        for (ADEmployeeSyncDto adSyncDto : syncDataList) {
            try {
                if (adSyncDto.getOrgHierarchyDto() == null || adSyncDto.getOrgHierarchyDto().isEmpty()) {
                    log.info("組織資訊為空, 員工編號: {}", adSyncDto.getEmployeeNo());
                    continue;
                } else {
                    log.info("處理同步數據, 員工編號: {}, 組織名稱: {}",
                            adSyncDto.getEmployeeNo(), adSyncDto.getOrgHierarchyDto().get(0).getOrgName());

                    adldapSyncService.syncEmployeeToAD(adSyncDto);
                }

            } catch (NamingException e) {
                handleSyncException("LDAP操作錯誤", adSyncDto, e);
            } catch (Exception e) {
                handleSyncException("未預期的錯誤", adSyncDto, e);
            }
        }
    }

    private void handleSyncException(String errorType, ADEmployeeSyncDto adSyncDto, Exception e) {
        log.error("{}, 員工編號: {}", errorType, adSyncDto.getEmployeeNo(), e);
        // 可以選擇在這裡添加更多的錯誤處理邏輯，例如發送通知或記錄到數據庫
    }

    @PostMapping("/employees/enable-e1Account")
    @Operation(summary = "啟用員工 AAD E1 帳號授權", description = "取得指定同步日期的員工資訊, 同步啟用員工 AAD E1 帳號授權")
    @ApiResponse(responseCode = "200", description = "同步啟用員工 AAD E1 帳號授權成功")
    @ApiResponse(responseCode = "400", description = "同步啟用員工 AAD E1 帳號授權失敗")
    @ApiResponse(responseCode = "500", description = "內部伺服器錯誤")
    public ResponseEntity<String> enableE1Account(
            @Parameter(description = "員工編號") @RequestParam(name = "employee-no", defaultValue = "") String employeeNo,
            @Parameter(description = "基準日期：日期之後的資料", schema = @Schema(type = "string", format = "date", example = "2025-02-28")) @RequestParam(name = "base-date", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        try {
            azureADService.enableAADE1Account(employeeNo, baseDate.toString());

            return ResponseEntity.ok("啟用員工 AAD E1 帳號授權成功");
        } catch (Exception e) {
            log.error("啟用員工 AAD E1 帳號授權過程中發生未知錯誤", e);
            return ResponseEntity.internalServerError().body("啟用員工 AAD E1 帳號授權過程中發生未知錯誤");
        }
    }

    @PostMapping("/employees/disable-e1Account")
    @Operation(summary = "停用員工 AAD E1 帳號授權", description = "取得指定同步日期的員工資訊, 停用員工 AAD E1 帳號授權")
    @ApiResponse(responseCode = "200", description = "同步停用員工 AAD E1 帳號授權成功")
    @ApiResponse(responseCode = "400", description = "同步停用員工 AAD E1 帳號授權失敗")
    @ApiResponse(responseCode = "500", description = "內部伺服器錯誤")
    public ResponseEntity<String> disableE1Account(
            @Parameter(description = "員工編號") @RequestParam(name = "employee-no", defaultValue = "") String employeeNo,
            @Parameter(description = "基準日期：日期之後的資料", schema = @Schema(type = "string", format = "date", example = "2025-02-28")) @RequestParam(name = "base-date", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        try {
            azureADService.disableAADE1Account(employeeNo, baseDate.toString());

            return ResponseEntity.ok("停用員工 AAD E1 帳號授權成功");
        } catch (Exception e) {
            log.error("停用員工 AAD E1 帳號授權過程中發生未知錯誤", e);
            return ResponseEntity.internalServerError().body("停用員工 AAD E1 帳號授權過程中發生未知錯誤");
        }
    }

    @PostMapping("/process-ad-organization-data")
    @Operation(summary = "處理 AD 組織同步數據", description = "從伺服器獲取 AD 組織同步數據並進行處理")
    @ApiResponse(responseCode = "200", description = "同步組織成功完成")
    @ApiResponse(responseCode = "204", description = "沒有組織數據需要同步")
    @ApiResponse(responseCode = "500", description = "同步過程中發生錯誤")
    public ResponseEntity<String> syncADOrganizationData(
            @Parameter(description = "基準日期：日期之後的資料", schema = @Schema(type = "string", format = "date", example = "2025-03-07")) @RequestParam(name = "base-date", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        try {
            List<ADOrganizationSyncDto> syncDataList = fetchADOrganizationSyncData(baseDate);
            if (syncDataList == null || syncDataList.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            log.info("獲取到 {} 條 AD 組織同步數據", syncDataList.size());
            processADOrganizationSyncData(syncDataList);
            return ResponseEntity.ok("組織同步成功完成");
        } catch (Exception e) {
            log.error("AD 組織同步過程中發生錯誤", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("組織同步過程中發生錯誤: " + e.getMessage());
        }
    }

    private List<ADOrganizationSyncDto> fetchADOrganizationSyncData(LocalDate baseDate) {
        String apiUrl = baseUrl + "/api/v1/ad-organization-sync-data";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);

        Map<String, String> params = new HashMap<>();
        params.put("base-date", baseDate != null ? baseDate.toString().trim() : "");

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl);
        if (params != null) {
            params.forEach(builder::queryParam);
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<ADOrganizationSyncDto>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<ADOrganizationSyncDto>>() {
                });

        return response.getBody();
    }

    private void processADOrganizationSyncData(List<ADOrganizationSyncDto> syncDataList) {
        // 處理組織同步
        for (ADOrganizationSyncDto orgSyncDto : syncDataList) {
            try {
                if (orgSyncDto.getOrgHierarchyDto() == null || orgSyncDto.getOrgHierarchyDto().isEmpty()) {
                    log.info("組織層級資訊為空, 組織代碼: {}", orgSyncDto.getOrgCode());
                    continue;
                } else {
                    log.info("處理組織同步數據, 組織代碼: {}, 組織名稱: {}",
                            orgSyncDto.getOrgCode(), orgSyncDto.getOrganization().getOrgName());

                    adldapSyncService.syncOrganizationToAD(orgSyncDto);
                }
            } catch (NamingException e) {
                handleOrganizationSyncException("LDAP操作錯誤", orgSyncDto, e);
            } catch (Exception e) {
                handleOrganizationSyncException("未預期的錯誤", orgSyncDto, e);
            }
        }
    }

    private void handleOrganizationSyncException(String errorType, ADOrganizationSyncDto orgSyncDto, Exception e) {
        log.error("{}, 組織代碼: {}", errorType, orgSyncDto.getOrgCode(), e);
        // 可以選擇在這裡添加更多的錯誤處理邏輯，例如發送通知或記錄到數據庫
    }
}