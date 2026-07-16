package com.travelmemory.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.config.AmapProperties;
import com.travelmemory.dto.CitySearchResult;
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
    private static final String AMAP_DISTRICT_SEARCH_URL = "https://restapi.amap.com/v3/config/district";

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
    public List<CitySearchResult> searchCities(String keyword) {
        String key = amapProperties.getWebService().getKey();
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(503, "城市搜索暂时不可用");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildDistrictSearchUrl(keyword, key)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("City search request failed with status {}", response.statusCode());
                throw new BusinessException(503, "城市搜索暂时不可用");
            }
            return parseDistrictSearchResponse(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.info("Failed to search cities for keyword {}", keyword, exception);
            throw new BusinessException(503, "城市搜索暂时不可用");
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

    private String buildDistrictSearchUrl(String keyword, String key) {
        return AMAP_DISTRICT_SEARCH_URL
                + "?output=json"
                + "&subdistrict=1"
                + "&extensions=base"
                + "&keywords=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    List<CitySearchResult> parseDistrictSearchResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(503, "城市搜索暂时不可用");
        }
        JsonNode districts = root.path("districts");
        if (!districts.isArray()) {
            return List.of();
        }

        java.util.ArrayList<DistrictCandidate> candidates = new java.util.ArrayList<>();
        collectDistricts(districts, candidates, 0, null, null, null);
        java.util.Set<String> seen = new java.util.HashSet<>();
        return candidates.stream()
                .map(this::toCitySearchResult)
                .filter(Objects::nonNull)
                .filter(result -> seen.add(result.id()))
                .limit(8)
                .toList();
    }

    private void collectDistricts(
            JsonNode districts,
            List<DistrictCandidate> output,
            int depth,
            String countryName,
            String provinceName,
            String cityName
    ) {
        if (!districts.isArray() || depth > 2) {
            return;
        }
        districts.forEach(district -> {
            String name = textOrNull(district.path("name"));
            String level = textOrNull(district.path("level"));
            String nextCountry = countryName;
            String nextProvince = provinceName;
            String nextCity = cityName;
            if ("country".equalsIgnoreCase(level)) {
                nextCountry = normalizeCountryName(name);
            } else {
                if (!StringUtils.hasText(nextCountry)) {
                    nextCountry = "中国";
                }
                if ("province".equalsIgnoreCase(level)) {
                    nextProvince = name;
                    if (StringUtils.hasText(name) && name.endsWith("市")) {
                        nextCity = name;
                    }
                } else if ("city".equalsIgnoreCase(level)) {
                    nextCity = name;
                }
            }
            output.add(new DistrictCandidate(district, nextCountry, nextProvince, nextCity));
            collectDistricts(district.path("districts"), output, depth + 1,
                    nextCountry, nextProvince, nextCity);
        });
    }

    private CitySearchResult toCitySearchResult(DistrictCandidate candidate) {
        JsonNode district = candidate.district();
        String id = textOrNull(district.path("adcode"));
        String name = textOrNull(district.path("name"));
        String level = textOrNull(district.path("level"));
        Coordinate coordinate = parseAmapCoordinate(textOrNull(district.path("center")));
        if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || coordinate == null
                || "country".equalsIgnoreCase(level)) {
            return null;
        }
        Coordinate wgs84 = CoordinateConverter.gcj02ToWgs84(
                BigDecimal.valueOf(coordinate.latitude()),
                BigDecimal.valueOf(coordinate.longitude())
        );
        return new CitySearchResult(
                id,
                name,
                level,
                candidate.countryName(),
                candidate.provinceName(),
                candidate.cityName(),
                "district".equalsIgnoreCase(level) ? name : null,
                toCoordinateDecimal(wgs84.latitude()),
                toCoordinateDecimal(wgs84.longitude())
        );
    }

    private ReverseGeocodeResult parseAmapResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            return ReverseGeocodeResult.failure(firstText(root.path("info"), root.path("infocode")));
        }

        JsonNode regeocode = root.path("regeocode");
        String formattedAddress = textOrNull(regeocode.path("formatted_address"));
        JsonNode addressComponent = regeocode.path("addressComponent");
        String countryName = normalizeCountryName(textOrNull(addressComponent.path("country")));
        String provinceName = textOrNull(addressComponent.path("province"));
        String cityName = textOrNull(addressComponent.path("city"));
        String districtName = textOrNull(addressComponent.path("district"));
        if (!StringUtils.hasText(cityName) && StringUtils.hasText(provinceName) && provinceName.endsWith("市")) {
            cityName = provinceName;
        }
        String administrativeName = StringUtils.hasText(cityName)
                ? cityName
                : (StringUtils.hasText(districtName) ? districtName : provinceName);
        List<LocationCandidate> candidates = locationNameSelector.candidates(regeocode);
        ReverseGeocodeResult result = candidates.stream().findFirst()
                .map(candidate -> toSuccessResult(candidate, formattedAddress, candidates))
                .orElseGet(() -> StringUtils.hasText(administrativeName)
                        ? ReverseGeocodeResult.success(administrativeName, formattedAddress, "amap")
                        : ReverseGeocodeResult.failure("暂时没有推荐出地点名称，你可以手动填写。"));
        result.setCountryName(countryName);
        result.setProvinceName(provinceName);
        result.setCityName(cityName);
        result.setDistrictName(districtName);
        return result;
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

    private String normalizeCountryName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return "中华人民共和国".equals(normalized) ? "中国" : normalized;
    }

    private record DistrictCandidate(
            JsonNode district,
            String countryName,
            String provinceName,
            String cityName
    ) {
    }
}
