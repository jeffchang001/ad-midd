package com.sogo.ad.midd.service.impl;

import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sogo.ad.midd.service.AzureADService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AzureADServiceImpl implements AzureADService {

    @Value("${azure.ad.tenant-id}")
    private String tenantId;

    @Value("${azure.ad.client-id}")
    private String clientId;

    @Value("${azure.ad.client-secret}")
    private String clientSecret;

    @Override
    public void enableAADE1Account(String employeeEmail) throws Exception {
        String userId = getUserIdByEmail(employeeEmail);
        String skuId = getSkuId();
        setUsageLocation(userId, "TW");
        assignE1License(userId, skuId, "C");
    }

    @Override
    public void disableAADE1Account(String employeeEmail) throws Exception {
        String userId = getUserIdByEmail(employeeEmail);
        String skuId = getSkuId();
        setUsageLocation(userId, "TW");
        assignE1License(userId, skuId, "D");
    }

    private String getAccessToken() throws IOException, ParseException {
        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            String body = "client_id=" + clientId +
                    "&scope=https://graph.microsoft.com/.default" +
                    "&client_secret=" + clientSecret +
                    "&grant_type=client_credentials";
            httpPost.setEntity(new StringEntity(body));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(jsonResponse);
                return rootNode.path("access_token").asText();
            }
        }
    }

    private String getUserIdByEmail(String userEmail) throws IOException, ParseException {
        String accessToken = getAccessToken();
        String url = "https://graph.microsoft.com/v1.0/users/" + userEmail;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "Bearer " + accessToken);
            httpGet.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = client.execute(httpGet)) {
                if (response.getCode() == 200) {
                    String jsonResponse = EntityUtils.toString(response.getEntity());
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(jsonResponse);
                    return rootNode.path("id").asText();
                } else {
                    log.info("Error fetching user ID: " + response.getCode());
                    return null;
                }
            }
        }
    }

    private String getSkuId() throws ParseException {
        String skuId = null;
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/subscribedSkus";
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet httpGet = new HttpGet(url);
                httpGet.setHeader("Authorization", "Bearer " + accessToken);
                httpGet.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    String jsonResponse = EntityUtils.toString(response.getEntity());
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(jsonResponse);
                    JsonNode skus = rootNode.path("value");
                    for (JsonNode sku : skus) {
                        log.info("SKU ID: " + sku.path("skuId").asText() +
                                ", SKU Name: " + sku.path("skuPartNumber").asText());
                        skuId = sku.path("skuId").asText();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return skuId;
    }

    private void setUsageLocation(String userId, String usageLocation) throws ParseException {
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userId;
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPatch httpPatch = new HttpPatch(url);
                httpPatch.setHeader("Authorization", "Bearer " + accessToken);
                httpPatch.setHeader("Content-Type", "application/json");

                String jsonBody = "{\"usageLocation\": \"" + usageLocation + "\"}";
                httpPatch.setEntity(new StringEntity(jsonBody));

                try (CloseableHttpResponse response = client.execute(httpPatch)) {
                    if (response.getCode() == 204) {
                        log.info("Usage location set to " + usageLocation + " for user " + userId);
                    } else {
                        log.info("Error setting usage location: " + response.getCode());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assignE1License(String userId, String skuID, String action) throws ParseException {
        try {
            String accessToken = getAccessToken();
            String url = "https://graph.microsoft.com/v1.0/users/" + userId + "/assignLicense";
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(url);
                httpPost.setHeader("Authorization", "Bearer " + accessToken);
                httpPost.setHeader("Content-Type", "application/json");

                String jsonBody = null;
                if (action.equals("C")) {
                    jsonBody = "{\"addLicenses\": [{\"skuId\": \"" + skuID + "\"}], \"removeLicenses\": []}";
                } else if (action.equals("D")) {
                    jsonBody = "{\"addLicenses\": [], \"removeLicenses\": [{\"skuId\": \"" + skuID + "\"}]}";
                } else {
                    log.info("Invalid action");
                }
                httpPost.setEntity(new StringEntity(jsonBody));

                try (CloseableHttpResponse response = client.execute(httpPost)) {
                    if (response.getCode() == 200) {
                        log.info("E1 license assigned successfully");
                    } else {
                        log.info("Error: " + response.getCode());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
