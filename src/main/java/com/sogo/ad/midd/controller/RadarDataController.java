package com.sogo.ad.midd.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.sogo.ad.midd.model.entity.APIEmployeeInfo;
import com.sogo.ad.midd.service.APIEmployeeInfoService;
import com.sogo.ad.midd.service.APIOrganizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Radar Data Sync", description = "提供從 Radar API 同步數據的功能")
public class RadarDataController {

    private final RestTemplate restTemplate;
    private final APIEmployeeInfoService apiEmployeeInfoService;
    private final APIOrganizationService apiOrganizationService;

    @Value("${radar.api.server.uri}")
    private String radarAPIServerURI;

    @Value("${radar.api.token}")
    private String radarAPIToken;

    @PostMapping("/system/initialization")
    @Operation(summary = "初始化系統", description = "執行系統初始化，同步會員資訊")
    @ApiResponse(responseCode = "200", description = "系統初始化成功")
    @ApiResponse(responseCode = "500", description = "系統初始化失敗")
    public ResponseEntity<String> initDatabase() {
        try {
            // 此同步數據順序也應該是未來各自 api 被呼叫的順序
            initialSyncEmployeeInfo("", null);
            initialSyncOrganizationInfo("", null);
            return ResponseEntity.ok("資料庫初始化成功");
        } catch (Exception e) {
            log.error("資料庫初始化失敗", e);
            return ResponseEntity.internalServerError().body("資料庫初始化失敗: " + e.getMessage());
        }
    }

    @PostMapping("/api/sync/employee-info")
    @Operation(summary = "同步員工資訊", description = "從 Radar API 同步員工資訊")
    @ApiResponse(responseCode = "200", description = "員工資訊同步成功")
    @ApiResponse(responseCode = "500", description = "員工資訊同步失敗")
    public ResponseEntity<String> initialSyncEmployeeInfo(
            @Parameter(description = "員工編號") @RequestParam(name = "employee-no", defaultValue = "") String employeeNo,
            @Parameter(description = "基準日期：日期之後的資料") @RequestParam(name = "base-date", defaultValue = "") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        try {
            String apiUrl = radarAPIServerURI + "/api/ZZApi/ZZEmployeeInfo";
            Map<String, String> params = new HashMap<>();
            params.put("employeeNos", employeeNo);
            params.put("baseDate", baseDate != null ? baseDate.toString() : "");
            ResponseEntity<String> response = fetchFromRadarAPI(apiUrl, params);
            apiEmployeeInfoService.initEmployeeInfo(response);
            return ResponseEntity.ok("員工資訊同步成功");
        } catch (Exception e) {
            log.error("員工資訊同步過程中發生未知錯誤", e);
            return ResponseEntity.internalServerError().body("員工資訊同步過程中發生未知錯誤");
        }
    }

    @PostMapping("/api/sync/organization-info")
    @Operation(summary = "同步組織資訊", description = "從 Radar API 同步組織資訊")
    @ApiResponse(responseCode = "200", description = "組織資訊同步成功")
    @ApiResponse(responseCode = "500", description = "組織資訊同步失敗")
    public ResponseEntity<String> initialSyncOrganizationInfo(
            @Parameter(description = "組織代碼") @RequestParam(name = "org-code", defaultValue = "") String orgCode,
            @Parameter(description = "基準日期：日期之後的資料") @RequestParam(name = "base-date", defaultValue = "") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        try {
            String apiUrl = radarAPIServerURI + "/api/ZZApi/ZZOrganization";
            Map<String, String> params = new HashMap<>();
            params.put("orgCodes", orgCode);
            params.put("baseDate", baseDate != null ? baseDate.toString().trim() : "");
            ResponseEntity<String> response = fetchFromRadarAPI(apiUrl, params);
            apiOrganizationService.initOrganizationInfo(response);
            return ResponseEntity.ok("組織資訊同步成功");
        } catch (Exception e) {
            log.error("組織資訊同步過程中發生未知錯誤", e);
            return ResponseEntity.internalServerError().body("組織資訊同步過程中發生未知錯誤: " + e.getMessage());
        }
    }

    private ResponseEntity<String> fetchFromRadarAPI(String apiUrl, Map<String, String> params)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-api-token", radarAPIToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl);
        if (params != null) {
            params.forEach(builder::queryParam);
        }

        try {
            return restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class);
        } catch (RestClientException e) {
            log.error("無法從 Radar API 獲取數據", e);
            throw new Exception("無法從 Radar API 獲取數據: " + e.getMessage(), e);
        }
    }

    @PostMapping("/api/ZZApi/ZZUpdateEmpEMailExtNo")
    @Operation(summary = "更新員工郵件地址與分機號碼", description = "更新員工的郵件地址與分機號碼，並同步到 Radar 系統")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "400", description = "請求參數錯誤")
    @ApiResponse(responseCode = "404", description = "找不到指定的員工")
    @ApiResponse(responseCode = "500", description = "內部伺服器錯誤")
    public ResponseEntity<String> updateEmployeeEmailAndExtNo(
            @Parameter(description = "員工編號") @RequestParam(name = "employee-no", defaultValue = "") String employeeNo) {
        log.info("收到更新員工郵件地址與分機號碼的請求: {}", employeeNo);

        try {
            // 查找員工資料
            APIEmployeeInfo employeeInfo = apiEmployeeInfoService.findByEmployeeNo(employeeNo);

            if (employeeInfo == null) {
                log.error("找不到員工編號為 {} 的員工資料", employeeNo);
                return ResponseEntity.notFound().build();
            }

            // 同步至 Radar 系統
            apiEmployeeInfoService.addEmployeeInfoEmailToRadar(employeeInfo);

            return ResponseEntity.ok("更新員工郵件地址與分機號碼成功");
        } catch (Exception e) {
            log.error("更新員工郵件地址與分機號碼時發生錯誤", e);
            return ResponseEntity.internalServerError().body("更新員工郵件地址與分機號碼時發生錯誤: " + e.getMessage());
        }
    }

}
