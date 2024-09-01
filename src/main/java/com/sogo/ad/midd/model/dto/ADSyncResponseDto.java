package com.sogo.ad.midd.model.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ADSyncResponseDto {
    private int httpStatusCode;
    private String errorCode;
    private List<ADSyncDto> result;
    private Map<String, String> extraData;
}