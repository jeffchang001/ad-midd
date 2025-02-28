package com.sogo.ad.midd.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sogo.ad.midd.model.entity.APIEmployeeInfoActionLog;

@Repository
public interface APIEmployeeInfoActionLogRepository extends JpaRepository<APIEmployeeInfoActionLog, Long> {

    List<APIEmployeeInfoActionLog> findByEmployeeNoAndAction(String employeeNo, String action);

    List<APIEmployeeInfoActionLog> findByEmployeeNoAndActionAndCreatedDate(String employeeNo, String action, LocalDateTime createdDate);

    @Query("SELECT log FROM APIEmployeeInfoActionLog log " +
       "WHERE function('to_char', log.createdDate, 'YYYY-MM-DD') = :date " +
       "AND log.action = :action")
    List<APIEmployeeInfoActionLog> findByAndActionAndCreatedDate(String action, @Param("date")String createdDate);

    List<APIEmployeeInfoActionLog> findByAction(String action);

}

