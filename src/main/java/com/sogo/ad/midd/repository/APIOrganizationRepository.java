package com.sogo.ad.midd.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sogo.ad.midd.model.entity.APIOrganization;

@Repository
public interface APIOrganizationRepository extends JpaRepository<APIOrganization, Long> {

    /**
     * 根據組織代碼查詢組織資訊
     * 
     * @param orgCode 組織代碼
     * @return 組織資訊
     */
    Optional<APIOrganization> findByOrgCode(String orgCode);

    /**
     * 根據公司代碼查詢組織資訊
     * 
     * @param companyCode 公司代碼
     * @return 組織資訊列表
     */
    List<APIOrganization> findByCompanyCode(String companyCode);

    /**
     * 查詢指定時間後更新的組織資訊
     * 
     * @param dataModifiedDate 資料修改時間
     * @return 組織資訊列表
     */
    List<APIOrganization> findByDataModifiedDateAfter(LocalDateTime dataModifiedDate);

    /**
     * 根據狀態查詢組織資訊
     * 
     * @param status 狀態
     * @return 組織資訊列表
     */
    List<APIOrganization> findByStatus(String status);
} 