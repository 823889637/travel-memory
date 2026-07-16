package com.travelmemory.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TripUpdateRequest {

    @NotBlank(message = "title is required")
    @Size(max = 100, message = "title must not exceed 100 characters")
    private String title;

    @Size(max = 100, message = "destination must not exceed 100 characters")
    private String destination;

    @DecimalMin(value = "-90", message = "destinationLatitude must be at least -90")
    @DecimalMax(value = "90", message = "destinationLatitude must not exceed 90")
    private BigDecimal destinationLatitude;

    @DecimalMin(value = "-180", message = "destinationLongitude must be at least -180")
    @DecimalMax(value = "180", message = "destinationLongitude must not exceed 180")
    private BigDecimal destinationLongitude;

    private LocalDate startDate;
    private LocalDate endDate;

    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    @Size(max = 1000, message = "notes must not exceed 1000 characters")
    private String notes;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public BigDecimal getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(BigDecimal destinationLatitude) { this.destinationLatitude = destinationLatitude; }
    public BigDecimal getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(BigDecimal destinationLongitude) { this.destinationLongitude = destinationLongitude; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "destinationLatitude and destinationLongitude must be provided together")
    public boolean isDestinationCoordinatePairValid() {
        return (destinationLatitude == null) == (destinationLongitude == null);
    }
}
