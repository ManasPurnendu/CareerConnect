package com.careerconnect.jobs.service;

import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.dto.JobSummaryDto;
import com.careerconnect.jobs.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RecruiterJobQueryService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterJobQueryService.class);

    private final JobRepository jobRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;

    public RecruiterJobQueryService(
            JobRepository jobRepository,
            RecruiterAuthRepository recruiterAuthRepository) {
        this.jobRepository = jobRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
    }

    /**
     * Retrieves a paginated list of jobs belonging strictly to the authenticated recruiter's company.
     * Ordering is deterministic: created_at DESC, id DESC.
     */
    public PageResult<JobSummaryDto> getRecruiterJobs(Long recruiterId, PageRequestDto pageRequest) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        List<JobSummaryDto> jobs = jobRepository.findJobsByCompanyIdPaginated(
                companyId,
                pageRequest.getOffset(),
                pageRequest.getSize()
        );

        long totalElements = jobRepository.countJobsByCompanyId(companyId);

        log.debug("Found {} recruiter jobs (total {}) for company {}", jobs.size(), totalElements, companyId);

        return PageResult.of(jobs, pageRequest.getPage(), pageRequest.getSize(), totalElements);
    }

    /**
     * Retrieves the detailed specifications of a job belonging strictly to the authenticated recruiter's company.
     * Throws 404 JOB_NOT_FOUND on non-existent jobs or cross-company access.
     */
    public JobDetailDto getRecruiterJobDetail(Long recruiterId, Long jobId) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        return jobRepository.findJobDetailByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found or not owned by your company.",
                        ErrorCode.JOB_NOT_FOUND
                ));
    }

    /**
     * Resolves recruiter's current company ID from the database (stale JWT protection).
     */
    private Long resolveAuthoritativeCompanyId(Long recruiterId) {
        if (recruiterId == null) {
            throw new ResourceNotFoundException("Recruiter identity not found.", ErrorCode.RESOURCE_NOT_FOUND);
        }

        return recruiterAuthRepository.findCompanyIdByRecruiterId(recruiterId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Recruiter must belong to a company to perform job operations.",
                        ErrorCode.COMPANY_REQUIRED
                ));
    }
}
