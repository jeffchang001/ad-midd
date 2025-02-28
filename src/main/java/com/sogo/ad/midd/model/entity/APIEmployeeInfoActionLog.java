package com.sogo.ad.midd.model.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_employee_info_action_log")
public class APIEmployeeInfoActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EmployeeNo", nullable = false)
    private String employeeNo;

    @Column(name = "PartyRoleID")
    private Long partyRoleID;

    @Column(name = "Action")
    private String action; // C (Create), U (Update), or D (Delete)

    @Column(name = "ActionDate")
    private LocalDateTime actionDate;

    @Column(name = "FieldName", nullable = false)
    private String fieldName; // 變更的欄位名稱，對於新增和刪除操作可以為null

    @Column(name = "OldValue")
    private String oldValue; // 變更前的值，對於新增操作為null

    @Column(name = "NewValue")
    private String newValue; // 變更後的值，對於刪除操作為null

    @Column(name = "CreatedDate", nullable = false)
    private LocalDateTime createdDate;

    public APIEmployeeInfoActionLog(String employeeNo, String action, String fieldName,
            String oldValue, String newValue) {
        this.employeeNo = employeeNo;
        this.action = action;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actionDate = LocalDateTime.now();
        this.createdDate = LocalDateTime.now();
    }
}
