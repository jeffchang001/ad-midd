package com.sogo.ad.midd.service;

public interface AzureADService {

    void enableAADE1Account(String employeeNo, String baseDate) throws Exception;

    void disableAADE1Account(String employeeNo, String baseDate) throws Exception;

    void enableAADE1AccountProcessor(String employeeEmail, String idNoSuffix) throws Exception;

    void disableAADE1AccountProcessor(String employeeEmail) throws Exception;
    
    void deleteAADE1AllAccountProcessor() throws Exception;

}
