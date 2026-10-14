package com.careerconnect.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateProjectRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @NotBlank(message = "Description cannot be blank")
    private String description;

    @Size(max = 255, message = "Project URL cannot exceed 255 characters")
    private String projectUrl;

    private Integer sortOrder;

    public CreateProjectRequest() {
    }

    public CreateProjectRequest(String title, String description, String projectUrl, Integer sortOrder) {
        this.title = title;
        this.description = description;
        this.projectUrl = projectUrl;
        this.sortOrder = sortOrder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProjectUrl() {
        return projectUrl;
    }

    public void setProjectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
