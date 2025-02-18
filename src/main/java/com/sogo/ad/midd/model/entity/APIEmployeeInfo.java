package com.sogo.ad.midd.model.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class APIEmployeeInfo {

    @JsonProperty("id")
    private Long id; // 您可以根據需要調整 ID 的類型
    
    @JsonProperty("PartyRoleID")
    private Long partyRoleID;

    @JsonProperty("FullName")
    private String fullName;

    @JsonProperty("EnglishName")
    private String englishName;

    @JsonProperty("EmployeeNo")
    private String employeeNo;

    @JsonProperty("IDNoSuffix")
    private String idNoSuffix;
    
    @JsonProperty("IDNo")
    private String idNo;

    @JsonProperty("PassportNo")
    private String passportNo;

    @JsonProperty("ARCIDNo")
    private String arcIDNo;

    @JsonProperty("GenderCode")
    private String genderCode;

    @JsonProperty("GenderName")
    private String genderName;

    @JsonProperty("DateOfBirth")
    private LocalDateTime dateOfBirth;

    @JsonProperty("CountryCode")
    private String countryCode;

    @JsonProperty("UserID")
    private String userID;

    @JsonProperty("JobFlag")
    private String jobFlag;

    @JsonProperty("JobGradeCode")
    private String jobGradeCode;

    @JsonProperty("JobGradeName")
    private String jobGradeName;

    @JsonProperty("JobLevelCode")
    private String jobLevelCode;

    @JsonProperty("JobLevelName")
    private String jobLevelName;

    @JsonProperty("JobTitleCode")
    private String jobTitleCode;

    @JsonProperty("JobTitleName")
    private String jobTitleName;

    @JsonProperty("PositionCode")
    private String positionCode;

    @JsonProperty("PositionName")
    private String positionName;

    @JsonProperty("EmployeeTypeCode")
    private String employeeTypeCode;

    @JsonProperty("EmployeeTypeName")
    private String employeeTypeName;

    @JsonProperty("EmployedStatus")
    private String employedStatus;

    @JsonProperty("CompanyPartyID")
    private Long companyPartyID;

    @JsonProperty("CompanyCode")
    private String companyCode;

    @JsonProperty("CompanyName")
    private String companyName;

    @JsonProperty("HireDate")
    private LocalDateTime hireDate;

    @JsonProperty("ResignationDate")
    private LocalDateTime resignationDate;

    @JsonProperty("FormulaOrgPartyID")
    private Long formulaOrgPartyID;

    @JsonProperty("FormulaOrgCode")
    private String formulaOrgCode;

    @JsonProperty("FormulaOrgName")
    private String formulaOrgName;

    @JsonProperty("FormOrgPartyID")
    private Long formOrgPartyID;

    @JsonProperty("FormOrgCode")
    private String formOrgCode;

    @JsonProperty("FormOrgName")
    private String formOrgName;

    @JsonProperty("EmailAddress")
    private String emailAddress;

    @JsonProperty("PresentPhoneNo")
    private String presentPhoneNo;

    @JsonProperty("PresentAddress")
    private String presentAddress;

    @JsonProperty("PresentZipCode")
    private String presentZipCode;

    @JsonProperty("PermanentPhoneNo")
    private String permanentPhoneNo;

    @JsonProperty("PermanentAddress")
    private String permanentAddress;

    @JsonProperty("PermanentZipCode")
    private String permanentZipCode;

    @JsonProperty("MobilePhoneNo")
    private String mobilePhoneNo;

    @JsonProperty("OfficePhone")
    private String officePhone;

    @JsonProperty("ExtNo")
    private String extNo;

    @JsonProperty("TenantID")
    private String tenantID;

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

    // @JsonProperty("FunctionOrgPartyID")
    // @Column(name = "FunctionOrgPartyID")
    // private String functionOrgPartyID;

    @JsonProperty("FunctionOrgCode")
    private String functionOrgCode;

    @JsonProperty("FunctionOrgName")
    private String functionOrgName;

    @JsonProperty("MVPN")
    private String mvpn;

    private String status;

}