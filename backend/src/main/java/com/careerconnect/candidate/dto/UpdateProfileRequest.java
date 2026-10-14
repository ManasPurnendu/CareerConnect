package com.careerconnect.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @NotBlank(message = "Full name cannot be blank")
    @Size(max = 150, message = "Full name cannot exceed 150 characters")
    private String fullName;

    @Size(max = 255, message = "Headline cannot exceed 255 characters")
    private String headline;

    @Size(max = 150, message = "Location cannot exceed 150 characters")
    private String location;

    private String summary;

    public UpdateProfileRequest() {
    }

    public UpdateProfileRequest(String fullName, String headline, String location, String summary) {
        this.fullName = fullName;
        this.headline = headline;
        this.location = location;
        this.summary = summary;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
