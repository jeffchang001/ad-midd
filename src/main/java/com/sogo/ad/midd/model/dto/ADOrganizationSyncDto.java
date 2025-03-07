package com.sogo.ad.midd.model.dto;

import java.util.List;
import java.util.Map;

import com.sogo.ad.midd.model.entity.APIOrganization;

import lombok.Data;

@Data
public class ADOrganizationSyncDto {
    private String orgCode;
    private String action;
    private APIOrganization organization;
    private List<OrganizationHierarchyDto> orgHierarchyDto;
    private Map<String, String> updatedFields;
}
