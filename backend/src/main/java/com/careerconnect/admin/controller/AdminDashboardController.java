package com.careerconnect.admin.controller;

import com.careerconnect.admin.dto.AdminDashboardDto;
import com.careerconnect.admin.service.AdminQueryService;
import com.careerconnect.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminQueryService adminQueryService;

    public AdminDashboardController(AdminQueryService adminQueryService) {
        this.adminQueryService = adminQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminDashboardDto>> getDashboard() {
        AdminDashboardDto dashboard = adminQueryService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }
}
