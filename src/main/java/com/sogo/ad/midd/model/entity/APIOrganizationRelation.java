package com.sogo.ad.midd.model.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class APIOrganizationRelation {
    
    @JsonProperty("id")
    private Long id;

    @JsonProperty("CompanyCode")
    private String companyCode;
    
    @JsonProperty("CompanyPartyID")
    private Long companyPartyId;
    
    @JsonProperty("CreatedDate")
    private LocalDateTime createdDate;

    @JsonProperty("DataCreatedDate")
    private LocalDateTime dataCreatedDate;

    @JsonProperty("DataCreatedUser")
    private String dataCreatedUser;

    @JsonProperty("DataModifiedDate")
    private LocalDateTime dataModifiedDate;

    @JsonProperty("DataModifiedUser")
    private String dataModifiedUser;

    @JsonProperty("OrgCode")
    private String orgCode;

    @JsonProperty("OrgName")
    private String orgName;

    @JsonProperty("OrgTreeType")
    private String orgTreeType;

    @JsonProperty("OrganizationRelationID")
    private Long organizationRelationId;

    @JsonProperty("ParentOrgCode")
    private String parentOrgCode;

    @JsonProperty("TenantID")
    private String tenantId;

    @JsonProperty("status")
    private String status;
}
