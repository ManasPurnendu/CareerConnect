package com.careerconnect.application.dto;

import jakarta.validation.constraints.Size;

public class ApplicationSubmissionRequest {

    @Size(max = 5000, message = "Cover letter must not exceed 5000 characters")
    private String coverLetter;

    public ApplicationSubmissionRequest() {
    }

    public ApplicationSubmissionRequest(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }
}
