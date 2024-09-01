package com.sogo.ad.midd.model.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sogo.ad.midd.model.entity.APIEmployeeInfo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class APIEmployeeInfoDto {
@JsonProperty("HttpStatusCode")
    private int httpStatusCode;

    @JsonProperty("ErrorCode")
    private String errorCode;

    @JsonProperty("Result")
    private List<APIEmployeeInfo> result;

    @JsonProperty("ExtraData")
    private Map<String, String> ExtraData; 
}
