package com.sogo.ad.midd.model.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sogo.ad.midd.model.entity.APIOrganizationRelation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class APIOrganizationRelationDto {
    @JsonProperty("HttpStatusCode")
    private int httpStatusCode;

    @JsonProperty("ErrorCode")
    private String errorCode;

    @JsonProperty("Result")
    private List<APIOrganizationRelation> result;

    @JsonProperty("ExtraData")
    private Map<String, String> ExtraData;
}
