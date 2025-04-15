package com.sogo.ad.midd.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.sogo.ad.midd.model.entity.APIOrganizationActionLog;

@Repository
public interface APIOrganizationActionLogRepository extends JpaRepository<APIOrganizationActionLog, Long> {
    
    /**
     * 根據組織代碼查詢未同步的動作記錄
     * 
     * @param orgCode 組織代碼
     * @return 未同步的組織動作記錄列表
     */
    List<APIOrganizationActionLog> findByOrgCodeAndIsSync(String orgCode, Boolean isSync);

    /**
     * 根據動作日期範圍查詢未同步的記錄
     * 
     * @param startDate 開始日期
     * @param endDate 結束日期
     * @return 未同步的組織動作記錄列表
     */
    List<APIOrganizationActionLog> findByActionDateBetweenAndIsSync(LocalDateTime startDate, LocalDateTime endDate, Boolean isSync);

    /**
     * 將指定的組織動作記錄標記為已同步
     * 
     * @param logIds 記錄ID列表
     * @return 更新記錄數
     */
    @Modifying
    @Transactional
    @Query("UPDATE APIOrganizationActionLog o SET o.isSync = true WHERE o.id IN :logIds")
    int updateSyncStatus(@Param("logIds") List<Long> logIds);
} 