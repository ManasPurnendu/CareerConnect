package com.careerconnect.company.controller;

import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.company.dto.CompanyDetailDto;
import com.careerconnect.company.dto.CompanySummaryDto;
import com.careerconnect.company.service.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanySummaryDto>>> getCompanies(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<CompanySummaryDto> pageResult = companyService.getPublicCompanies(q, page, size);
        return ResponseEntity.ok(ApiResponse.success(pageResult.getItems(), pageResult.getPagination()));
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyDetailDto>> getCompanyById(@PathVariable Long companyId) {
        CompanyDetailDto company = companyService.getPublicCompanyDetail(companyId);
        return ResponseEntity.ok(ApiResponse.success(company));
    }
}
