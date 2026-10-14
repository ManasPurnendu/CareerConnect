package com.careerconnect.auth.dto;

import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthUserDto {

    private Long id;
    private String email;
    private Role role;
    private String fullName;
    private Long candidateId;
    private Long recruiterId;
    private Long companyId;

    public AuthUserDto() {
    }

    public AuthUserDto(Long id, String email, Role role, String fullName, Long candidateId, Long recruiterId, Long companyId) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.candidateId = candidateId;
        this.recruiterId = recruiterId;
        this.companyId = companyId;
    }

    public static AuthUserDto forCandidate(Long id, String email, String fullName, Long candidateId) {
        return new AuthUserDto(id, email, Role.CANDIDATE, fullName, candidateId, null, null);
    }

    public static AuthUserDto forRecruiter(Long id, String email, String fullName, Long recruiterId, Long companyId) {
        return new AuthUserDto(id, email, Role.RECRUITER, fullName, null, recruiterId, companyId);
    }

    public static AuthUserDto forAdmin(Long id, String email, String fullName) {
        return new AuthUserDto(id, email, Role.ADMIN, fullName, null, null, null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getRecruiterId() {
        return recruiterId;
    }

    public void setRecruiterId(Long recruiterId) {
        this.recruiterId = recruiterId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }
}
