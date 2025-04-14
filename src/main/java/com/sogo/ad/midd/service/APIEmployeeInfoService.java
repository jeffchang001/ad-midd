package com.sogo.ad.midd.service;

import org.springframework.http.ResponseEntity;

import com.sogo.ad.midd.model.entity.APIEmployeeInfo;

public interface APIEmployeeInfoService {

    public void initEmployeeInfo(ResponseEntity<String> response) throws Exception;

    public APIEmployeeInfo findByEmployeeNo(String employeeNo) throws Exception;

    public void addEmployeeInfoEmailToRadar(APIEmployeeInfo employeeInfo) throws Exception;

}
