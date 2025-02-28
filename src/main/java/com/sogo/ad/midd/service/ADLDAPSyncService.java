package com.sogo.ad.midd.service;

import com.sogo.ad.midd.model.dto.ADSyncDto;

public interface ADLDAPSyncService {
    void syncEmployeeToAD(ADSyncDto employeeData) throws Exception;

    void syncOrganizationToAD(ADSyncDto organizationData) throws Exception;

    void enableAADE1Account(String baseDate) throws Exception;

    void disableAADE1Account(String baseDate) throws Exception;
}
