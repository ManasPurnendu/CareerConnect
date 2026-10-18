package com.careerconnect.admin.service;

import com.careerconnect.admin.dto.AdminApplicationDetailDto;
import com.careerconnect.admin.dto.AdminApplicationSummaryDto;
import com.careerconnect.admin.dto.AdminCompanyDto;
import com.careerconnect.admin.dto.AdminDashboardDto;
import com.careerconnect.admin.dto.AdminJobDto;
import com.careerconnect.admin.dto.AdminUserDto;
import com.careerconnect.admin.repository.AdminQueryRepository;
import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicationStatusHistoryDto;
import com.careerconnect.application.dto.CandidateSnapshot;
import com.careerconnect.application.dto.JobMatchDto;
import com.careerconnect.application.dto.ResumeSnapshot;
import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.application.service.MatchService;
import com.careerconnect.candidate.repository.CandidateResumeRepository;
import com.careerconnect.candidate.service.CandidateResumeService;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.model.Role;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service coordinating platform-wide administrative read-only operations.
 */
@Service
public class AdminQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminQueryService.class);

    private final AdminQueryRepository adminQueryRepository;
    private final ApplicationRepository applicationRepository;
    private final CandidateResumeRepository candidateResumeRepository;
    private final CandidateResumeService candidateResumeService;
    private final JobRepository jobRepository;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;

    public AdminQueryService(
            AdminQueryRepository adminQueryRepository,
            ApplicationRepository applicationRepository,
            CandidateResumeRepository candidateResumeRepository,
            CandidateResumeService candidateResumeService,
            JobRepository jobRepository,
            MatchService matchService,
            ObjectMapper objectMapper) {
        this.adminQueryRepository = adminQueryRepository;
        this.applicationRepository = applicationRepository;
        this.candidateResumeRepository = candidateResumeRepository;
        this.candidateResumeService = candidateResumeService;
        this.jobRepository = jobRepository;
        this.matchService = matchService;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------
    // Dashboard Summary
    // -------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminDashboardDto getDashboard() {
        return adminQueryRepository.getDashboardSummary();
    }

    // -------------------------------------------------------------
    // Users Listing
    // -------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<AdminUserDto> getUsers(Role role, Boolean active, String q, PageRequestDto pageRequest) {
        PageRequestDto pr = pageRequest != null ? pageRequest : new PageRequestDto();
        int limit = pr.getSize();
        int offset = pr.getOffset();

        List<AdminUserDto> items = adminQueryRepository.findUsers(role, active, q, limit, offset);
        long total = adminQueryRepository.countUsers(role, active, q);

        return PageResult.of(items, pr.getPage(), pr.getSize(), total);
    }

    // -------------------------------------------------------------
    // Companies Listing
    // -------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<AdminCompanyDto> getCompanies(Boolean active, String q, PageRequestDto pageRequest) {
        PageRequestDto pr = pageRequest != null ? pageRequest : new PageRequestDto();
        int limit = pr.getSize();
        int offset = pr.getOffset();

        List<AdminCompanyDto> items = adminQueryRepository.findCompanies(active, q, limit, offset);
        long total = adminQueryRepository.countCompanies(active, q);

        return PageResult.of(items, pr.getPage(), pr.getSize(), total);
    }

    // -------------------------------------------------------------
    // Jobs Listing
    // -------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<AdminJobDto> getJobs(JobStatus status, Long companyId, String q, PageRequestDto pageRequest) {
        PageRequestDto pr = pageRequest != null ? pageRequest : new PageRequestDto();
        int limit = pr.getSize();
        int offset = pr.getOffset();

        List<AdminQueryRepository.AdminJobEntity> entities = adminQueryRepository.findJobs(status, companyId, q, limit, offset);
        long total = adminQueryRepository.countJobs(status, companyId, q);

        List<AdminJobDto> items = entities.stream()
                .map(e -> {
                    List<String> skills = adminQueryRepository.findSkillsByJobId(e.id());
                    return new AdminJobDto(
                            e.id(),
                            e.companyId(),
                            e.companyName(),
                            e.title(),
                            e.description(),
                            e.jobType(),
                            e.experienceLevel(),
                            e.workMode(),
                            e.location(),
                            e.salaryMinLpa(),
                            e.salaryMaxLpa(),
                            e.applicationDeadline(),
                            e.status(),
                            e.applicationCount(),
                            skills,
                            e.createdAt(),
                            e.updatedAt()
                    );
                })
                .toList();

        return PageResult.of(items, pr.getPage(), pr.getSize(), total);
    }

    // -------------------------------------------------------------
    // Applications Listing & Detail
    // -------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<AdminApplicationSummaryDto> getApplications(
            Long jobId, Long candidateId, Long companyId, ApplicationStatus status, PageRequestDto pageRequest) {
        PageRequestDto pr = pageRequest != null ? pageRequest : new PageRequestDto();
        int limit = pr.getSize();
        int offset = pr.getOffset();

        List<AdminQueryRepository.AdminApplicationSummaryEntity> entities =
                adminQueryRepository.findApplications(jobId, candidateId, companyId, status, limit, offset);
        long total = adminQueryRepository.countApplications(jobId, candidateId, companyId, status);

        List<AdminApplicationSummaryDto> items = entities.stream()
                .map(e -> new AdminApplicationSummaryDto(
                        e.id(),
                        e.jobId(),
                        e.jobTitle(),
                        e.companyId(),
                        e.companyName(),
                        e.candidateId(),
                        e.candidateName(),
                        e.candidateEmail(),
                        e.status(),
                        e.appliedAt(),
                        e.createdAt(),
                        e.updatedAt()
                ))
                .toList();

        return PageResult.of(items, pr.getPage(), pr.getSize(), total);
    }

    @Transactional(readOnly = true)
    public AdminApplicationDetailDto getApplicationDetail(Long applicationId) {
        AdminQueryRepository.AdminApplicationDetailRecord record = adminQueryRepository
                .findApplicationDetailById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        CandidateSnapshot candidateSnapshot = parseCandidateSnapshot(record.candidateSnapshotJson());
        ResumeSnapshot resumeSnapshot = parseResumeSnapshot(record.resumeSnapshotJson());

        List<String> jobSkills = jobRepository.findRequiredSkillNamesByJobId(record.jobId());
        List<String> candidateSkills = (candidateSnapshot != null && candidateSnapshot.skills() != null)
                ? candidateSnapshot.skills()
                : List.of();

        JobMatchDto match = matchService.calculateMatch(jobSkills, candidateSkills);

        List<ApplicationStatusHistoryDto> history = applicationRepository
                .findStatusHistoryByApplicationId(applicationId)
                .stream()
                .map(h -> new ApplicationStatusHistoryDto(
                        h.id(),
                        h.applicationId(),
                        h.fromStatus(),
                        h.toStatus(),
                        h.changedByUserId(),
                        h.note(),
                        h.createdAt()
                ))
                .toList();

        return new AdminApplicationDetailDto(
                record.applicationId(),
                record.jobId(),
                record.jobTitle(),
                record.companyId(),
                record.companyName(),
                record.candidateId(),
                record.status(),
                record.coverLetter(),
                record.appliedAt(),
                candidateSnapshot,
                resumeSnapshot,
                history,
                match
        );
    }

    // -------------------------------------------------------------
    // Protected Resume Access
    // -------------------------------------------------------------

    public record AdminResumeDownloadDto(
            Long id,
            String filename,
            String contentType,
            long sizeBytes,
            byte[] content,
            boolean isCurrent,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @Transactional(readOnly = true)
    public AdminResumeDownloadDto getApplicationResume(Long applicationId) {
        AdminQueryRepository.AdminApplicationDetailRecord application = adminQueryRepository
                .findApplicationDetailById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        com.careerconnect.candidate.repository.CandidateResumeRepository.ResumeEntity resume = candidateResumeRepository
                .findCurrentByCandidateId(application.candidateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume not found for applicant in application " + applicationId + ".",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        CandidateResumeService.ResumeFileResource file = candidateResumeService
                .getResumeFile(resume.storageKey(), resume.originalFilename(), resume.contentType());

        return new AdminResumeDownloadDto(
                resume.id(),
                file.filename(),
                file.contentType(),
                file.sizeBytes(),
                file.content(),
                resume.isCurrent(),
                resume.createdAt(),
                resume.updatedAt()
        );
    }

    // -------------------------------------------------------------
    // Snapshot Helpers
    // -------------------------------------------------------------

    public CandidateSnapshot parseCandidateSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), CandidateSnapshot.class);
            }
            return objectMapper.treeToValue(node, CandidateSnapshot.class);
        } catch (Exception e) {
            log.error("Failed to deserialize candidate snapshot: {}", e.getMessage());
            throw new IllegalStateException("Failed to deserialize candidate snapshot", e);
        }
    }

    public ResumeSnapshot parseResumeSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), ResumeSnapshot.class);
            }
            return objectMapper.treeToValue(node, ResumeSnapshot.class);
        } catch (Exception e) {
            log.error("Failed to deserialize resume snapshot: {}", e.getMessage());
            throw new IllegalStateException("Failed to deserialize resume snapshot", e);
        }
    }
}
