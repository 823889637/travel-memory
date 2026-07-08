package com.travelmemory.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.dto.ReverseGeocodeResult;
import com.travelmemory.service.LocationService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocationServiceImpl implements LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);
    private static final String AMAP_REVERSE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/regeo";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${map.reverse-geocode.key:}")
    private String reverseGeocodeKey;

    @Value("${amap.web.key:}")
    private String amapWebKey;

    public LocationServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!isValidCoordinate(latitude, longitude)) {
            return ReverseGeocodeResult.empty();
        }

        String key = getConfiguredKey();
        if (!StringUtils.hasText(key)) {
            return ReverseGeocodeResult.empty();
        }

        try {
            String url = buildAmapUrl(latitude, longitude, key);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("Reverse geocode request failed with status {}", response.statusCode());
                return ReverseGeocodeResult.empty();
            }
            return parseAmapResponse(response.body());
        } catch (Exception e) {
            log.info("Failed to reverse geocode location {}, {}", latitude, longitude, e);
            return ReverseGeocodeResult.empty();
        }
    }

    private String getConfiguredKey() {
        if (StringUtils.hasText(reverseGeocodeKey)) {
            return reverseGeocodeKey;
        }
        return amapWebKey;
    }

    private boolean isValidCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private String buildAmapUrl(BigDecimal latitude, BigDecimal longitude, String key) {
        String location = longitude.toPlainString() + "," + latitude.toPlainString();
        return AMAP_REVERSE_GEOCODE_URL
                + "?output=json"
                + "&extensions=all"
                + "&location=" + location
                + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    private ReverseGeocodeResult parseAmapResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            return ReverseGeocodeResult.empty();
        }

        JsonNode regeocode = root.path("regeocode");
        String formattedAddress = textOrNull(regeocode.path("formatted_address"));
        String locationName = firstText(
                regeocode.path("pois").path(0).path("name"),
                regeocode.path("aois").path(0).path("name"),
                regeocode.path("addressComponent").path("township"),
                regeocode.path("addressComponent").path("district"),
                regeocode.path("formatted_address")
        );

        if (!StringUtils.hasText(locationName) && !StringUtils.hasText(formattedAddress)) {
            return ReverseGeocodeResult.empty();
        }
        if (!StringUtils.hasText(locationName)) {
            locationName = formattedAddress;
        }
        return ReverseGeocodeResult.success(locationName, formattedAddress, "amap");
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = textOrNull(node);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
