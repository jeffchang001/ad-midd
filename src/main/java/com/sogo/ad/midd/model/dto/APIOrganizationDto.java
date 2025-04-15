package com.sogo.ad.midd.model.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sogo.ad.midd.model.entity.APIOrganization;

import lombok.Data;

@Data
public class APIOrganizationDto {
    @JsonProperty("HttpStatusCode")
    private int httpStatusCode;

    @JsonProperty("ErrorCode")
    private String errorCode;

    @JsonProperty("Result")
    private List<APIOrganization> result;

    @JsonProperty("ExtraData")
    private Map<String, String> extraData;
} 