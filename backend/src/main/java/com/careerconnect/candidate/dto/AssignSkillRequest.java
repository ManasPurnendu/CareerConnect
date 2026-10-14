package com.careerconnect.candidate.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AssignSkillRequest {

    @NotBlank(message = "Skill name cannot be blank")
    @Size(max = 100, message = "Skill name cannot exceed 100 characters")
    private String name;

    public AssignSkillRequest() {
    }

    public AssignSkillRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter("skillName")
    public void setSkillName(String skillName) {
        if (this.name == null || this.name.isBlank()) {
            this.name = skillName;
        }
    }
}
