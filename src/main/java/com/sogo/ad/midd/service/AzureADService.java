package com.sogo.ad.midd.service;

public interface AzureADService {

    void enableAADE1Account(String employeeNo, String baseDate) throws Exception;

    void disableAADE1Account(String employeeNo, String baseDate) throws Exception;

}
