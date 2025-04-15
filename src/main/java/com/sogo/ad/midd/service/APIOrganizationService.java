package com.sogo.ad.midd.service;

import org.springframework.http.ResponseEntity;

import com.sogo.ad.midd.model.entity.APIOrganization;

public interface APIOrganizationService {

    /**
     * 初始化組織資訊
     * 
     * @param response Radar API 回應資料
     * @throws Exception 處理異常
     */
    public void initOrganizationInfo(ResponseEntity<String> response) throws Exception;

    /**
     * 根據組織代碼查詢組織資訊
     * 
     * @param orgCode 組織代碼
     * @return 組織資訊
     * @throws Exception 處理異常
     */
    public APIOrganization findByOrgCode(String orgCode) throws Exception;

} 