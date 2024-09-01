package com.sogo.ad.midd.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationHierarchyDto {
    private String orgCode;
    private String orgName;
    private String parentOrgCode;
    private int orgLevel;
}
