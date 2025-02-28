package com.sogo.ad.midd.service;

public interface AzureADService {

    void enableAADE1Account(String employeeEmail) throws Exception;

    void disableAADE1Account(String employeeEmail) throws Exception;

}
