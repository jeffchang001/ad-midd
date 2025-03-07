package com.sogo.ad.midd.service;

import com.sogo.ad.midd.model.dto.ADEmployeeSyncDto;
import com.sogo.ad.midd.model.dto.ADOrganizationSyncDto;

public interface ADLDAPSyncService {
    void syncEmployeeToAD(ADEmployeeSyncDto employeeData) throws Exception;

    void syncOrganizationToAD(ADOrganizationSyncDto organizationData) throws Exception;

}
