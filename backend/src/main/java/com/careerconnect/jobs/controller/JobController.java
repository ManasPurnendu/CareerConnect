package com.careerconnect.jobs.controller;

import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.dto.JobFilterParams;
import com.careerconnect.jobs.dto.JobSummaryDto;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import com.careerconnect.jobs.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobSummaryDto>>> getJobs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) ExperienceLevel experienceLevel,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) BigDecimal minSalaryLpa,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        JobFilterParams params = new JobFilterParams(
                q, location, jobType, experienceLevel, workMode, skill, minSalaryLpa, sort, page, size
        );

        PageResult<JobSummaryDto> pageResult = jobService.getPublicJobs(params);
        return ResponseEntity.ok(ApiResponse.success(pageResult.getItems(), pageResult.getPagination()));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobDetailDto>> getJobById(@PathVariable Long jobId) {
        JobDetailDto job = jobService.getPublicJobDetail(jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }
}
