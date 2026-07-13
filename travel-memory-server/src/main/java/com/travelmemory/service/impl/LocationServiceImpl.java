package com.travelmemory.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.config.AmapProperties;
import com.travelmemory.dto.CoordinateNormalizeRequest;
import com.travelmemory.dto.CoordinateNormalizeResult;
import com.travelmemory.dto.LocationNameCandidate;
import com.travelmemory.dto.LocationSearchResult;
import com.travelmemory.dto.ReverseGeocodeResult;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.service.LocationService;
import com.travelmemory.util.CoordinateConverter;
import com.travelmemory.util.CoordinateConverter.Coordinate;
import com.travelmemory.util.LocationCandidate;
import com.travelmemory.util.LocationNameSelector;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocationServiceImpl implements LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);
    private static final String AMAP_REVERSE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final String AMAP_TEXT_SEARCH_URL = "https://restapi.amap.com/v3/place/text";

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

    @Override
    public List<LocationSearchResult> search(String keyword, BigDecimal latitude, BigDecimal longitude, String city) {
        if (latitude != null && !isValidCoordinate(latitude, longitude)) {
            throw new BusinessException(400, "坐标无效");
        }
        String key = amapProperties.getWebService().getKey();
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(503, "地点搜索暂时不可用");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildTextSearchUrl(keyword, latitude, longitude, city, key)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("Location search request failed with status {}", response.statusCode());
                throw new BusinessException(503, "地点搜索暂时不可用");
            }
            return parseTextSearchResponse(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.info("Failed to search locations for keyword {}", keyword, exception);
            throw new BusinessException(503, "地点搜索暂时不可用");
        }
    }

    @Override
    public CoordinateNormalizeResult normalize(CoordinateNormalizeRequest request) {
        BigDecimal latitude = request.getLatitude();
        BigDecimal longitude = request.getLongitude();
        if (!isValidCoordinate(latitude, longitude)) {
            throw new BusinessException(400, "坐标无效");
        }

        String coordinateSystem = request.getCoordinateSystem().trim().toUpperCase(Locale.ROOT);
        Coordinate normalized;
        if ("WGS84".equals(coordinateSystem)) {
            normalized = new Coordinate(latitude.doubleValue(), longitude.doubleValue());
        } else if ("GCJ02".equals(coordinateSystem)) {
            normalized = CoordinateConverter.gcj02ToWgs84(latitude, longitude);
        } else {
            throw new BusinessException(400, "不支持的坐标系");
        }

        return new CoordinateNormalizeResult(
                toCoordinateDecimal(normalized.latitude()),
                toCoordinateDecimal(normalized.longitude()),
                "WGS84"
        );
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

    private String buildTextSearchUrl(
            String keyword,
            BigDecimal latitude,
            BigDecimal longitude,
            String city,
            String key
    ) {
        StringBuilder url = new StringBuilder(AMAP_TEXT_SEARCH_URL)
                .append("?output=json")
                .append("&offset=10")
                .append("&page=1")
                .append("&extensions=base")
                .append("&keywords=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                .append("&key=").append(URLEncoder.encode(key, StandardCharsets.UTF_8));

        if (latitude != null && longitude != null && CoordinateConverter.isInChina(latitude, longitude)) {
            Coordinate coordinate = CoordinateConverter.wgs84ToGcj02(latitude, longitude);
            url.append("&location=").append(coordinate.longitude()).append(',').append(coordinate.latitude())
                    .append("&sortrule=distance");
        }
        if (StringUtils.hasText(city)) {
            url.append("&city=").append(URLEncoder.encode(city.trim(), StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private ReverseGeocodeResult parseAmapResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            return ReverseGeocodeResult.failure(firstText(root.path("info"), root.path("infocode")));
        }

        JsonNode regeocode = root.path("regeocode");
        String formattedAddress = textOrNull(regeocode.path("formatted_address"));
        List<LocationCandidate> candidates = locationNameSelector.candidates(regeocode);
        return candidates.stream().findFirst()
                .map(candidate -> toSuccessResult(candidate, formattedAddress, candidates))
                .orElseGet(() -> ReverseGeocodeResult.failure("暂时没有推荐出地点名称，你可以手动填写。"));
    }

    private ReverseGeocodeResult toSuccessResult(
            LocationCandidate candidate,
            String formattedAddress,
            List<LocationCandidate> candidates
    ) {
        List<LocationNameCandidate> nameCandidates = candidates.stream()
                .filter(value -> StringUtils.hasText(value.getName()))
                .filter(distinctByName())
                .limit(5)
                .map(value -> new LocationNameCandidate(value.getName(), value.getDistance()))
                .toList();
        return ReverseGeocodeResult.success(candidate.getName(), formattedAddress, "amap", nameCandidates);
    }

    private List<LocationSearchResult> parseTextSearchResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(503, "地点搜索暂时不可用");
        }
        JsonNode pois = root.path("pois");
        if (!pois.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(pois.spliterator(), false)
                .map(this::toSearchResult)
                .filter(Objects::nonNull)
                .limit(10)
                .toList();
    }

    private LocationSearchResult toSearchResult(JsonNode poi) {
        String name = textOrNull(poi.path("name"));
        Coordinate coordinate = parseAmapCoordinate(textOrNull(poi.path("location")));
        if (!StringUtils.hasText(name) || coordinate == null) {
            return null;
        }
        Coordinate wgs84 = CoordinateConverter.gcj02ToWgs84(
                BigDecimal.valueOf(coordinate.latitude()),
                BigDecimal.valueOf(coordinate.longitude())
        );
        return new LocationSearchResult(
                textOrNull(poi.path("id")),
                name,
                textOrNull(poi.path("address")),
                textOrNull(poi.path("adname")),
                toCoordinateDecimal(wgs84.latitude()),
                toCoordinateDecimal(wgs84.longitude()),
                parseDistance(poi.path("distance"))
        );
    }

    private Coordinate parseAmapCoordinate(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String[] values = location.split(",");
        if (values.length != 2) {
            return null;
        }
        try {
            BigDecimal longitude = new BigDecimal(values[0].trim());
            BigDecimal latitude = new BigDecimal(values[1].trim());
            return isValidCoordinate(latitude, longitude)
                    ? new Coordinate(latitude.doubleValue(), longitude.doubleValue())
                    : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseDistance(JsonNode node) {
        String value = textOrNull(node);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal toCoordinateDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }

    private java.util.function.Predicate<LocationCandidate> distinctByName() {
        java.util.Set<String> names = new java.util.HashSet<>();
        return candidate -> names.add(candidate.getName().trim());
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
