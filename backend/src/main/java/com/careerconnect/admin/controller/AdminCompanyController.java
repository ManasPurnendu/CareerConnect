package com.careerconnect.admin.controller;

import com.careerconnect.admin.dto.AdminCompanyDto;
import com.careerconnect.admin.dto.AdminUpdateCompanyStatusRequest;
import com.careerconnect.admin.service.AdminMutationService;
import com.careerconnect.admin.service.AdminQueryService;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/companies")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompanyController {

    private final AdminQueryService adminQueryService;
    private final AdminMutationService adminMutationService;

    public AdminCompanyController(
            AdminQueryService adminQueryService,
            AdminMutationService adminMutationService) {
        this.adminQueryService = adminQueryService;
        this.adminMutationService = adminMutationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCompanyDto>>> getCompanies(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        PageRequestDto pageRequest = new PageRequestDto(page, size);
        PageResult<AdminCompanyDto> result = adminQueryService.getCompanies(active, q, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    @PatchMapping("/{companyId}/status")
    public ResponseEntity<ApiResponse<AdminCompanyDto>> updateCompanyStatus(
            @PathVariable Long companyId,
            @Valid @RequestBody AdminUpdateCompanyStatusRequest request) {

        AdminCompanyDto updated = adminMutationService.updateCompanyStatus(companyId, request.isActive());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
