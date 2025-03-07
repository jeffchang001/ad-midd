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
@Table(name = "api_organization_relation_action_log")
public class APIOrganizationRelationActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "OrgCode", nullable = false)
    private String orgCode;

    @Column(name = "OrganizationRelationId")
    private Long organizationRelationId;

    @Column(name = "Action", nullable = false)
    private String action; // C (Create), U (Update), or D (Delete)

    @Column(name = "ActionDate", nullable = false)
    private LocalDateTime actionDate;

    @Column(name = "FieldName", nullable = false)
    private String fieldName;

    @Column(name = "OldValue")
    private String oldValue;

    @Column(name = "NewValue")
    private String newValue;

    @Column(name = "CreatedDate", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "IsSync")
    private Boolean isSync = Boolean.FALSE;

    public APIOrganizationRelationActionLog(String orgCode, String action, String fieldName, String oldValue,
            String newValue) {
        this.orgCode = orgCode;
        this.action = action;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actionDate = LocalDateTime.now();
        this.createdDate = LocalDateTime.now();
    }
}