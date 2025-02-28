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
@Table(name = "api_employee_info") // 假設您的數據庫表名稱為 APIEmployeeInfo
public class APIEmployeeInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 或其他適合您數據庫的策略
    private Long id; // 您可以根據需要調整 ID 的類型

    @JsonProperty("PartyRoleID")
    @Column(name = "PartyRoleID")
    private Long partyRoleID;

    @JsonProperty("FullName")
    @Column(name = "FullName")
    private String fullName;

    @JsonProperty("EnglishName")
    @Column(name = "EnglishName")
    private String englishName;

    @JsonProperty("EmployeeNo")
    @Column(name = "EmployeeNo")
    private String employeeNo;

    @JsonProperty("IDNoSuffix")
    @Column(name = "IDNoSuffix")
    private String idNoSuffix;
    
    @JsonProperty("IDNo")
    @Column(name = "IDNo")
    private String idNo;

    @JsonProperty("PassportNo")
    @Column(name = "PassportNo")
    private String passportNo;

    @JsonProperty("ARCIDNo")
    @Column(name = "ARCIDNo")
    private String arcIDNo;

    @JsonProperty("GenderCode")
    @Column(name = "GenderCode")
    private String genderCode;

    @JsonProperty("GenderName")
    @Column(name = "GenderName")
    private String genderName;

    @JsonProperty("DateOfBirth")
    @Column(name = "DateOfBirth")
    private LocalDateTime dateOfBirth;

    @JsonProperty("CountryCode")
    @Column(name = "CountryCode")
    private String countryCode;

    @JsonProperty("UserID")
    @Column(name = "UserID")
    private String userID;

    @JsonProperty("JobFlag")
    @Column(name = "JobFlag")
    private String jobFlag;

    @JsonProperty("JobGradeCode")
    @Column(name = "JobGradeCode")
    private String jobGradeCode;

    @JsonProperty("JobGradeName")
    @Column(name = "JobGradeName")
    private String jobGradeName;

    @JsonProperty("JobLevelCode")
    @Column(name = "JobLevelCode")
    private String jobLevelCode;

    @JsonProperty("JobLevelName")
    @Column(name = "JobLevelName")
    private String jobLevelName;

    @JsonProperty("JobTitleCode")
    @Column(name = "JobTitleCode")
    private String jobTitleCode;

    @JsonProperty("JobTitleName")
    @Column(name = "JobTitleName")
    private String jobTitleName;

    @JsonProperty("PositionCode")
    @Column(name = "PositionCode")
    private String positionCode;

    @JsonProperty("PositionName")
    @Column(name = "PositionName")
    private String positionName;

    @JsonProperty("EmployeeTypeCode")
    @Column(name = "EmployeeTypeCode")
    private String employeeTypeCode;

    @JsonProperty("EmployeeTypeName")
    @Column(name = "EmployeeTypeName")
    private String employeeTypeName;

    @JsonProperty("EmployedStatus")
    @Column(name = "EmployedStatus")
    private String employedStatus;

    @JsonProperty("CompanyPartyID")
    @Column(name = "CompanyPartyID")
    private Long companyPartyID;

    @JsonProperty("CompanyCode")
    @Column(name = "CompanyCode")
    private String companyCode;

    @JsonProperty("CompanyName")
    @Column(name = "CompanyName")
    private String companyName;

    @JsonProperty("HireDate")
    @Column(name = "HireDate")
    private LocalDateTime hireDate;

    @JsonProperty("ResignationDate")
    @Column(name = "ResignationDate")
    private LocalDateTime resignationDate;

    @JsonProperty("FormulaOrgPartyID")
    @Column(name = "FormulaOrgPartyID")
    private Long formulaOrgPartyID;

    @JsonProperty("FormulaOrgCode")
    @Column(name = "FormulaOrgCode")
    private String formulaOrgCode;

    @JsonProperty("FormulaOrgName")
    @Column(name = "FormulaOrgName")
    private String formulaOrgName;

    @JsonProperty("FormOrgPartyID")
    @Column(name = "FormOrgPartyID")
    private Long formOrgPartyID;

    @JsonProperty("FormOrgCode")
    @Column(name = "FormOrgCode")
    private String formOrgCode;

    @JsonProperty("FormOrgName")
    @Column(name = "FormOrgName")
    private String formOrgName;

    @JsonProperty("EmailAddress")
    @Column(name = "EmailAddress")
    private String emailAddress;

    @JsonProperty("PresentPhoneNo")
    @Column(name = "PresentPhoneNo")
    private String presentPhoneNo;

    @JsonProperty("PresentAddress")
    @Column(name = "PresentAddress")
    private String presentAddress;

    @JsonProperty("PresentZipCode")
    @Column(name = "PresentZipCode")
    private String presentZipCode;

    @JsonProperty("PermanentPhoneNo")
    @Column(name = "PermanentPhoneNo")
    private String permanentPhoneNo;

    @JsonProperty("PermanentAddress")
    @Column(name = "PermanentAddress")
    private String permanentAddress;

    @JsonProperty("PermanentZipCode")
    @Column(name = "PermanentZipCode")
    private String permanentZipCode;

    @JsonProperty("MobilePhoneNo")
    @Column(name = "MobilePhoneNo")
    private String mobilePhoneNo;

    @JsonProperty("OfficePhone")
    @Column(name = "OfficePhone")
    private String officePhone;

    @JsonProperty("ExtNo")
    @Column(name = "ExtNo")
    private String extNo;

    @JsonProperty("TenantID")
    @Column(name = "TenantID")
    private String tenantID;

    @JsonProperty("CreatedDate")
    @Column(name = "CreatedDate")
    private LocalDateTime createdDate;

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

    // @JsonProperty("FunctionOrgPartyID")
    // @Column(name = "FunctionOrgPartyID")
    // private String functionOrgPartyID;

    @JsonProperty("FunctionOrgCode")
    @Column(name = "FunctionOrgCode")
    private String functionOrgCode;

    @JsonProperty("FunctionOrgName")
    @Column(name = "FunctionOrgName")
    private String functionOrgName;

    @JsonProperty("MVPN")
    @Column(name = "MVPN")
    private String mvpn;

    @Column(name = "Status")
    private String status;

}