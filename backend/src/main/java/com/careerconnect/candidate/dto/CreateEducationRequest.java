package com.careerconnect.candidate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateEducationRequest {

    @NotBlank(message = "Institution cannot be blank")
    @Size(max = 200, message = "Institution cannot exceed 200 characters")
    private String institution;

    @NotBlank(message = "Degree cannot be blank")
    @Size(max = 100, message = "Degree cannot exceed 100 characters")
    private String degree;

    @NotBlank(message = "Field of study cannot be blank")
    @Size(max = 100, message = "Field of study cannot exceed 100 characters")
    private String fieldOfStudy;

    @NotNull(message = "Start year is required")
    @Min(value = 1900, message = "Start year must be at least 1900")
    @Max(value = 2100, message = "Start year must be at most 2100")
    private Integer startYear;

    @Min(value = 1900, message = "End year must be at least 1900")
    @Max(value = 2100, message = "End year must be at most 2100")
    private Integer endYear;

    @Size(max = 50, message = "Grade cannot exceed 50 characters")
    private String grade;

    private Integer sortOrder;

    public CreateEducationRequest() {
    }

    public CreateEducationRequest(String institution, String degree, String fieldOfStudy,
                                  Integer startYear, Integer endYear, String grade, Integer sortOrder) {
        this.institution = institution;
        this.degree = degree;
        this.fieldOfStudy = fieldOfStudy;
        this.startYear = startYear;
        this.endYear = endYear;
        this.grade = grade;
        this.sortOrder = sortOrder;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
    }

    public Integer getStartYear() {
        return startYear;
    }

    public void setStartYear(Integer startYear) {
        this.startYear = startYear;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public void setEndYear(Integer endYear) {
        this.endYear = endYear;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
