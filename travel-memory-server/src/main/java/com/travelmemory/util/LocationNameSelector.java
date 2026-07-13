package com.travelmemory.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocationNameSelector {

    private static final int MISSING_DISTANCE = Integer.MAX_VALUE;

    public Optional<LocationCandidate> select(JsonNode regeocode) {
        return candidates(regeocode).stream().findFirst();
    }

    public List<LocationCandidate> candidates(JsonNode regeocode) {
        List<LocationCandidate> candidates = new ArrayList<>();
        JsonNode addressComponent = regeocode.path("addressComponent");

        addAois(candidates, regeocode.path("aois"));
        addNestedName(candidates, addressComponent.path("building"), LocationCandidate.Source.BUILDING);
        addNestedName(candidates, addressComponent.path("neighborhood"), LocationCandidate.Source.NEIGHBORHOOD);
        addPois(candidates, regeocode.path("pois"));
        addBusinessAreas(candidates, addressComponent.path("businessAreas"));
        addName(candidates, addressComponent.path("township"), LocationCandidate.Source.TOWNSHIP, null, 0);
        addName(candidates, regeocode.path("formatted_address"), LocationCandidate.Source.ADDRESS, null, 0);

        return candidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.getName()))
                .sorted(Comparator
                        .comparingInt((LocationCandidate candidate) -> candidate.getSource().getRank())
                        .thenComparing(candidate -> -distanceValue(candidate))
                        .thenComparing(candidate -> -candidate.getIndex())
                        .thenComparing(candidate -> -simpleLengthScore(candidate))
                        .reversed())
                .toList();
    }

    private void addAois(List<LocationCandidate> candidates, JsonNode aois) {
        if (!aois.isArray()) {
            return;
        }
        for (int i = 0; i < aois.size(); i++) {
            JsonNode aoi = aois.get(i);
            Integer distance = parseDistance(aoi.path("distance"));
            LocationCandidate.Source source = distance != null && distance == 0
                    ? LocationCandidate.Source.AOI_CONTAINS
                    : LocationCandidate.Source.AOI_NEAR;
            addName(candidates, aoi.path("name"), source, distance, i);
        }
    }

    private void addPois(List<LocationCandidate> candidates, JsonNode pois) {
        if (!pois.isArray()) {
            return;
        }
        for (int i = 0; i < pois.size(); i++) {
            JsonNode poi = pois.get(i);
            addName(candidates, poi.path("name"), LocationCandidate.Source.POI, parseDistance(poi.path("distance")), i);
        }
    }

    private void addBusinessAreas(List<LocationCandidate> candidates, JsonNode businessAreas) {
        if (!businessAreas.isArray()) {
            return;
        }
        for (int i = 0; i < businessAreas.size(); i++) {
            addName(candidates, businessAreas.get(i).path("name"), LocationCandidate.Source.BUSINESS_AREA, null, i);
        }
    }

    private void addNestedName(List<LocationCandidate> candidates, JsonNode node, LocationCandidate.Source source) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            addName(candidates, node.path("name"), source, null, 0);
            return;
        }
        addName(candidates, node, source, null, 0);
    }

    private void addName(
            List<LocationCandidate> candidates,
            JsonNode node,
            LocationCandidate.Source source,
            Integer distance,
            int index
    ) {
        String name = textOrNull(node);
        if (!StringUtils.hasText(name)) {
            return;
        }
        candidates.add(new LocationCandidate(name.trim(), source, distance, index));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray() || node.isObject()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private Integer parseDistance(JsonNode node) {
        String value = textOrNull(node);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int distanceValue(LocationCandidate candidate) {
        return candidate.getDistance() == null ? MISSING_DISTANCE : candidate.getDistance();
    }

    private int simpleLengthScore(LocationCandidate candidate) {
        String name = candidate.getName();
        return name == null ? 0 : -name.length();
    }
}
