package com.sogo.ad.midd.service;

import java.time.LocalDate;

import com.sogo.ad.midd.model.dto.ADEmployeeSyncDto;
import com.sogo.ad.midd.model.dto.ADOrganizationSyncDto;

public interface ADLDAPSyncService {
    void syncEmployeeToAD(ADEmployeeSyncDto employeeData, LocalDate baseDate) throws Exception;

    void syncOrganizationToAD(ADOrganizationSyncDto organizationData) throws Exception;

}
