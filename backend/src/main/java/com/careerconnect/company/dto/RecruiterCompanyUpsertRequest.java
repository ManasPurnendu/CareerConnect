package com.careerconnect.company.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecruiterCompanyUpsertRequest(
        @NotBlank(message = "Company name cannot be blank")
        @Size(max = 150, message = "Company name cannot exceed 150 characters")
        String name,

        @Size(max = 255, message = "Logo URL cannot exceed 255 characters")
        String logo,

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        String description,

        @Size(max = 100, message = "Industry cannot exceed 100 characters")
        String industry,

        @Size(max = 255, message = "Website URL cannot exceed 255 characters")
        String website,

        @Size(max = 150, message = "Location cannot exceed 150 characters")
        String location,

        @Size(max = 50, message = "Company size cannot exceed 50 characters")
        String companySize
) {
    public RecruiterCompanyUpsertRequest {
        if (name != null) {
            name = name.trim();
        }
        if (logo != null) {
            logo = logo.trim();
        }
        if (industry != null) {
            industry = industry.trim();
        }
        if (website != null) {
            website = website.trim();
        }
        if (location != null) {
            location = location.trim();
        }
        if (description != null) {
            description = description.trim();
        }
        if (companySize != null) {
            companySize = companySize.trim();
        }
    }
}
