package com.sogo.ad.midd.model.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_organization")
public class APIOrganization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("OrganizationID")
    @Column(name = "OrganizationID")
    private Long organizationID;

    @JsonProperty("CompanyCode")
    @Column(name = "CompanyCode")
    private String companyCode;

    @JsonProperty("CompanyName")
    @Column(name = "CompanyName")
    private String companyName;

    @JsonProperty("OrgCode")
    @Column(name = "OrgCode")
    private String orgCode;

    @JsonProperty("OrgName")
    @Column(name = "OrgName")
    private String orgName;

    @JsonProperty("EnglishName")
    @Column(name = "EnglishName")
    private String englishName;

    @JsonProperty("StartDate")
    @Column(name = "StartDate")
    private LocalDateTime startDate;

    @JsonProperty("EndDate")
    @Column(name = "EndDate")
    private LocalDateTime endDate;

    @JsonProperty("OrgPropertyCode")
    @Column(name = "OrgPropertyCode")
    private String orgPropertyCode;

    @JsonProperty("Remark")
    @Column(name = "Remark")
    private String remark;

    @JsonProperty("Address")
    @Column(name = "Address")
    private String address;

    @JsonProperty("Email")
    @Column(name = "Email")
    private String email;

    @JsonProperty("Telephone")
    @Column(name = "Telephone")
    private String telephone;

    @JsonProperty("Fax")
    @Column(name = "Fax")
    private String fax;

    @JsonProperty("LayerCode")
    @Column(name = "LayerCode")
    private String layerCode;

    @JsonProperty("LayerName")
    @Column(name = "LayerName")
    private String layerName;

    @JsonProperty("SortSequence")
    @Column(name = "SortSequence")
    private Integer sortSequence;

    @JsonProperty("CreatedDate")
    @Column(name = "CreatedDate")
    private LocalDateTime createdDate;

    @JsonProperty("CompanyPartyID")
    @Column(name = "CompanyPartyID")
    private Long companyPartyID;

    @JsonProperty("TenantID")
    @Column(name = "TenantID")
    private String tenantID;

    @JsonProperty("DataCreatedDate")
    @Column(name = "DataCreatedDate")
    private LocalDateTime dataCreatedDate;

    @JsonProperty("DataCreatedUser")
    @Column(name = "DataCreatedUser")
    private String dataCreatedUser;

    @JsonProperty("DataModifiedDate")
    @Column(name = "DataModifiedDate")
    private LocalDateTime dataModifiedDate;

    @JsonProperty("DataModifiedUser")
    @Column(name = "DataModifiedUser")
    private String dataModifiedUser;

    @Column(name = "Status")
    private String status;
}
