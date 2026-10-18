package com.careerconnect.company.service;

import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.common.exception.ConflictException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.company.dto.RecruiterCompanyResponseDto;
import com.careerconnect.company.dto.RecruiterCompanyUpsertRequest;
import com.careerconnect.company.repository.CompanyRepository;
import com.careerconnect.company.repository.CompanyRepository.CompanyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RecruiterCompanyService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterCompanyService.class);

    private final CompanyRepository companyRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;

    public RecruiterCompanyService(
            CompanyRepository companyRepository,
            RecruiterAuthRepository recruiterAuthRepository) {
        this.companyRepository = companyRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
    }

    @Transactional(readOnly = true)
    public RecruiterCompanyResponseDto getCompany(Long recruiterId) {
        if (recruiterId == null) {
            throw new ResourceNotFoundException("Recruiter not found.", ErrorCode.RESOURCE_NOT_FOUND);
        }

        Optional<Long> companyIdOpt = recruiterAuthRepository.findCompanyIdByRecruiterId(recruiterId);
        if (companyIdOpt.isEmpty() || companyIdOpt.get() == null) {
            throw new ResourceNotFoundException(
                    "Recruiter does not currently have an associated company.",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        Long companyId = companyIdOpt.get();
        CompanyRecord company = companyRepository.findCompanyById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Associated company with ID " + companyId + " not found.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        return toDto(company);
    }

    @Transactional
    public RecruiterCompanyResponseDto upsertCompany(Long recruiterId, RecruiterCompanyUpsertRequest request) {
        if (recruiterId == null) {
            throw new ResourceNotFoundException("Recruiter not found.", ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!recruiterAuthRepository.existsById(recruiterId)) {
            throw new ResourceNotFoundException("Recruiter with ID " + recruiterId + " not found.");
        }

        Optional<Long> existingCompanyIdOpt = recruiterAuthRepository.findCompanyIdByRecruiterId(recruiterId);

        if (existingCompanyIdOpt.isEmpty() || existingCompanyIdOpt.get() == null) {
            // CASE A: Recruiter has no company -> Create new company and associate recruiter
            return createCompanyAndAssociate(recruiterId, request);
        } else {
            // CASE B: Recruiter already belongs to a company -> Update existing company
            Long companyId = existingCompanyIdOpt.get();
            return updateExistingCompany(companyId, request);
        }
    }

    private RecruiterCompanyResponseDto createCompanyAndAssociate(Long recruiterId, RecruiterCompanyUpsertRequest request) {
        if (companyRepository.existsByNameAndNotId(request.name(), null)) {
            throw new ConflictException(
                    "A company with the name '" + request.name() + "' already exists.",
                    ErrorCode.COMPANY_ALREADY_EXISTS
            );
        }

        Long companyId = companyRepository.insertCompany(
                request.name(),
                request.industry(),
                request.location(),
                request.website(),
                request.description(),
                request.companySize()
        );

        boolean associated = recruiterAuthRepository.updateCompanyId(recruiterId, companyId);
        if (!associated) {
            throw new IllegalStateException("Failed to associate recruiter with the newly created company.");
        }

        log.info("Created new company: companyId={}, recruiterId={}, name='{}'", companyId, recruiterId, request.name());

        CompanyRecord company = companyRepository.findCompanyById(companyId)
                .orElseThrow(() -> new IllegalStateException("Newly created company could not be re-queried: id=" + companyId));

        return toDto(company);
    }

    private RecruiterCompanyResponseDto updateExistingCompany(Long companyId, RecruiterCompanyUpsertRequest request) {
        if (companyRepository.existsByNameAndNotId(request.name(), companyId)) {
            throw new ConflictException(
                    "Another company with the name '" + request.name() + "' already exists.",
                    ErrorCode.COMPANY_ALREADY_EXISTS
            );
        }

        boolean updated = companyRepository.updateCompany(
                companyId,
                request.name(),
                request.industry(),
                request.location(),
                request.website(),
                request.description(),
                request.companySize()
        );

        if (!updated) {
            throw new ResourceNotFoundException("Company with ID " + companyId + " not found.");
        }

        log.info("Updated company: companyId={}, name='{}'", companyId, request.name());

        CompanyRecord company = companyRepository.findCompanyById(companyId)
                .orElseThrow(() -> new IllegalStateException("Updated company could not be re-queried: id=" + companyId));

        return toDto(company);
    }

    private RecruiterCompanyResponseDto toDto(CompanyRecord record) {
        return RecruiterCompanyResponseDto.of(
                record.id(),
                record.name(),
                record.description(),
                record.industry(),
                record.website(),
                record.location(),
                record.companySize(),
                record.isActive(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
