package com.sogo.ad.midd.service;

import com.sogo.ad.midd.model.dto.ADSyncDto;

public interface ADLDAPSyncService {
    void syncEmployeeToLDAP(ADSyncDto employeeData) throws Exception;
    void syncOrganizationToLDAP(ADSyncDto organizationData) throws Exception;
}
