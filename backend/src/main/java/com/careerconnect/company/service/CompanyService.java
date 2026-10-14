package com.careerconnect.company.service;

import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.company.dto.CompanyDetailDto;
import com.careerconnect.company.dto.CompanySummaryDto;
import com.careerconnect.company.repository.CompanyRepository;
import com.careerconnect.jobs.dto.JobSummaryDto;
import com.careerconnect.jobs.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;

    public CompanyService(CompanyRepository companyRepository, JobRepository jobRepository) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
    }

    public PageResult<CompanySummaryDto> getPublicCompanies(String q, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = (size <= 0) ? 20 : Math.min(size, 100);
        int offset = boundedPage * boundedSize;

        List<CompanySummaryDto> companies = companyRepository.findActiveCompanies(q, boundedSize, offset);
        long totalElements = companyRepository.countActiveCompanies(q);

        return PageResult.of(companies, boundedPage, boundedSize, totalElements);
    }

    public CompanyDetailDto getPublicCompanyDetail(Long id) {
        CompanyDetailDto company = companyRepository.findActiveCompanyDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company with ID " + id + " not found"));

        List<JobSummaryDto> activeJobs = jobRepository.findActiveJobsByCompanyId(id);

        return new CompanyDetailDto(
                company.id(),
                company.name(),
                company.industry(),
                company.location(),
                company.website(),
                company.description(),
                company.companySize(),
                activeJobs.size(),
                activeJobs,
                company.createdAt()
        );
    }
}
