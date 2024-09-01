package com.sogo.ad.midd.model.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class APIEmployeeInfoActionLog {

    private Long id;

    private String employeeNo;

    private Long partyRoleID;

    private String action; // C (Create), U (Update), or D (Delete)

    private LocalDateTime actionDate;

    private String fieldName; // 變更的欄位名稱，對於新增和刪除操作可以為null

    private String oldValue; // 變更前的值，對於新增操作為null

    private String newValue; // 變更後的值，對於刪除操作為null

    private LocalDateTime createdDate;

    private Boolean isSync = Boolean.FALSE;

}
