package com.sogo.ad.midd.model.dto;

import java.util.List;
import java.util.Map;

import com.sogo.ad.midd.model.entity.APIEmployeeInfo;

import lombok.Data;

@Data
public class ADSyncDto {

    private String employeeNo;
    private String action;
    private APIEmployeeInfo employeeInfo;
    private List<OrganizationHierarchyDto> orgHierarchyDto;
    private Map<String, String> updatedFields;

}