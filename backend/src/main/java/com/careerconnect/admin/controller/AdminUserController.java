package com.careerconnect.admin.controller;

import com.careerconnect.admin.dto.AdminUpdateUserStatusRequest;
import com.careerconnect.admin.dto.AdminUserDto;
import com.careerconnect.admin.service.AdminMutationService;
import com.careerconnect.admin.service.AdminQueryService;
import com.careerconnect.common.model.Role;
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
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminQueryService adminQueryService;
    private final AdminMutationService adminMutationService;

    public AdminUserController(
            AdminQueryService adminQueryService,
            AdminMutationService adminMutationService) {
        this.adminQueryService = adminQueryService;
        this.adminMutationService = adminMutationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserDto>>> getUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        PageRequestDto pageRequest = new PageRequestDto(page, size);
        PageResult<AdminUserDto> result = adminQueryService.getUsers(role, active, q, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<AdminUserDto>> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest request) {

        AdminUserDto updated = adminMutationService.updateUserStatus(userId, request.isActive());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
