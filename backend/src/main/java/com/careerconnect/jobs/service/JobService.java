package com.careerconnect.jobs.service;

import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.dto.JobFilterParams;
import com.careerconnect.jobs.dto.JobSummaryDto;
import com.careerconnect.jobs.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public PageResult<JobSummaryDto> getPublicJobs(JobFilterParams params) {
        List<JobSummaryDto> jobs = jobRepository.findActiveJobs(params);
        long totalElements = jobRepository.countActiveJobs(params);

        return PageResult.of(jobs, params.getPage(), params.getSize(), totalElements);
    }

    public JobDetailDto getPublicJobDetail(Long id) {
        return jobRepository.findActiveJobById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job with ID " + id + " not found", ErrorCode.JOB_NOT_FOUND));
    }
}
