package com.travelmemory.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.config.AmapProperties;
import com.travelmemory.dto.ReverseGeocodeResult;
import com.travelmemory.service.LocationService;
import com.travelmemory.util.CoordinateConverter;
import com.travelmemory.util.CoordinateConverter.Coordinate;
import com.travelmemory.util.LocationCandidate;
import com.travelmemory.util.LocationNameSelector;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocationServiceImpl implements LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);
    private static final String AMAP_REVERSE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/regeo";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AmapProperties amapProperties;
    private final LocationNameSelector locationNameSelector;

    public LocationServiceImpl(
            ObjectMapper objectMapper,
            AmapProperties amapProperties,
            LocationNameSelector locationNameSelector
    ) {
        this.objectMapper = objectMapper;
        this.amapProperties = amapProperties;
        this.locationNameSelector = locationNameSelector;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!isValidCoordinate(latitude, longitude)) {
            return ReverseGeocodeResult.failure("坐标无效，暂时无法推荐地点");
        }

        if (!amapProperties.getReverseGeocode().isEnabled()) {
            return ReverseGeocodeResult.failure("地点推荐能力暂未启用");
        }

        String key = amapProperties.getWebService().getKey();
        if (!StringUtils.hasText(key)) {
            return ReverseGeocodeResult.failure("未配置高德 WebService Key");
        }

        if (!CoordinateConverter.isInChina(latitude, longitude)) {
            return ReverseGeocodeResult.failure("当前坐标暂时无法推荐地点");
        }

        try {
            Coordinate coordinate = CoordinateConverter.wgs84ToGcj02(latitude, longitude);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildAmapUrl(coordinate, key)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("Reverse geocode request failed with status {}", response.statusCode());
                return ReverseGeocodeResult.failure("地点推荐暂时不可用");
            }
            return parseAmapResponse(response.body());
        } catch (Exception e) {
            log.info("Failed to reverse geocode location {}, {}", latitude, longitude, e);
            return ReverseGeocodeResult.failure("地点推荐暂时不可用");
        }
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

    private String buildAmapUrl(Coordinate coordinate, String key) {
        String location = coordinate.longitude() + "," + coordinate.latitude();
        return AMAP_REVERSE_GEOCODE_URL
                + "?output=json"
                + "&extensions=all"
                + "&radius=300"
                + "&location=" + location
                + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    private ReverseGeocodeResult parseAmapResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            return ReverseGeocodeResult.failure(firstText(root.path("info"), root.path("infocode")));
        }

        JsonNode regeocode = root.path("regeocode");
        String formattedAddress = textOrNull(regeocode.path("formatted_address"));
        return locationNameSelector.select(regeocode)
                .map(candidate -> toSuccessResult(candidate, formattedAddress))
                .orElseGet(() -> ReverseGeocodeResult.failure("暂时没有推荐出地点名称，你可以手动填写。"));
    }

    private ReverseGeocodeResult toSuccessResult(LocationCandidate candidate, String formattedAddress) {
        return ReverseGeocodeResult.success(candidate.getName(), formattedAddress, "amap");
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
        if (node.isObject() || node.isArray()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
