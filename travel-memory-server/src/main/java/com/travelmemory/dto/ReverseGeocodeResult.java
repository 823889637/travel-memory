package com.travelmemory.dto;

import java.util.List;

public class ReverseGeocodeResult {

    private String locationName;
    private String formattedAddress;
    private String source;
    private boolean success;
    private String message;
    private List<LocationNameCandidate> candidates = List.of();

    public static ReverseGeocodeResult empty() {
        return failure(null);
    }

    public static ReverseGeocodeResult failure(String message) {
        ReverseGeocodeResult result = new ReverseGeocodeResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    public static ReverseGeocodeResult success(String locationName, String formattedAddress, String source) {
        return success(locationName, formattedAddress, source, List.of());
    }

    public static ReverseGeocodeResult success(
            String locationName,
            String formattedAddress,
            String source,
            List<LocationNameCandidate> candidates
    ) {
        ReverseGeocodeResult result = new ReverseGeocodeResult();
        result.setLocationName(locationName);
        result.setFormattedAddress(formattedAddress);
        result.setSource(source);
        result.setSuccess(true);
        result.setMessage(null);
        result.setCandidates(candidates == null ? List.of() : candidates);
        return result;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<LocationNameCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<LocationNameCandidate> candidates) {
        this.candidates = candidates == null ? List.of() : candidates;
    }
}
