package com.sogo.ad.midd.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sogo.ad.midd.model.entity.APIEmployeeInfo;

@Repository
public interface APIEmployeeInfoRepository extends JpaRepository<APIEmployeeInfo, Long> {
    
    @Modifying
    @Query(value = "TRUNCATE TABLE api_employee_info", nativeQuery = true)
    void truncateTable();

    // 自動生成的方法，查詢 employedStatus = 1 的記錄
    List<APIEmployeeInfo> findByEmployedStatus(String employedStatus);
    
    // 新增的查詢方法：查詢 employedStatus = '1' 且 status = 'C' 的記錄
    List<APIEmployeeInfo> findByEmployedStatusAndStatus(String employedStatus, String status);

    // 自動生成的方法，查詢 employedStatus = 1 的記錄
    List<APIEmployeeInfo> findByStatus(String status);

    APIEmployeeInfo findByEmployeeNo(String employeeNo);

    @Query(value = "SELECT * FROM get_employees_by_org_code(:orgCode, :orgTreeType)", nativeQuery = true)
    List<APIEmployeeInfo> findEmployeesByOrgCode(@Param("orgCode") String orgCode, @Param("orgTreeType") String orgTreeType);

}
