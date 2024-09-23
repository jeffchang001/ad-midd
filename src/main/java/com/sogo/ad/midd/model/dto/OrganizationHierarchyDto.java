package com.sogo.ad.midd.model.dto;

import lombok.Data;

@Data
public class OrganizationHierarchyDto {
    private String orgCode;
    private String orgName;
    private String parentOrgCode;
    private int orgLevel;
}
