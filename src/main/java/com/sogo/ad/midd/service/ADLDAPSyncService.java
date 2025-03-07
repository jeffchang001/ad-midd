package com.sogo.ad.midd.service;

import com.sogo.ad.midd.model.dto.ADEmployeeSyncDto;

public interface ADLDAPSyncService {
    void syncEmployeeToAD(ADEmployeeSyncDto employeeData) throws Exception;

    void syncOrganizationToAD(ADEmployeeSyncDto organizationData) throws Exception;

}
