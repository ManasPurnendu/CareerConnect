package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.ResumeResponse;
import com.careerconnect.candidate.repository.CandidateResumeRepository;
import com.careerconnect.candidate.repository.CandidateResumeRepository.ResumeEntity;
import com.careerconnect.common.exception.BaseApiException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.exception.UnauthorizedException;
import com.careerconnect.common.exception.ValidationException;
import com.careerconnect.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Service
public class CandidateResumeService {

    private static final Logger log = LoggerFactory.getLogger(CandidateResumeService.class);
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MiB
    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-

    private final CandidateResumeRepository candidateResumeRepository;
    private final String uploadDir;

    public CandidateResumeService(
            CandidateResumeRepository candidateResumeRepository,
            @Value("${app.resume.upload-dir:./uploads/resumes}") String uploadDir) {
        this.candidateResumeRepository = candidateResumeRepository;
        this.uploadDir = uploadDir;
    }

    public ResumeResponse uploadResume(MultipartFile file) {
        return uploadResume(SecurityUtils.getRequiredCandidateId(), file);
    }

    @Transactional
    public ResumeResponse uploadResume(Long candidateId, MultipartFile file) {
        if (candidateId == null) {
            throw new UnauthorizedException("Candidate identity required.");
        }

        validateFile(file);

        Path uploadPath;
        try {
            uploadPath = getUploadDirectory();
        } catch (IOException e) {
            log.error("Failed to initialize resume upload directory", e);
            throw new RuntimeException("Could not initialize upload storage.", e);
        }

        String storageKey = UUID.randomUUID().toString() + ".pdf";
        Path targetFile = resolveStoragePath(uploadPath, storageKey);

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to write resume file to storage", e);
            throw new RuntimeException("Failed to store resume file.", e);
        }

        Optional<ResumeEntity> previousOpt = candidateResumeRepository.findCurrentByCandidateId(candidateId);

        try {
            if (previousOpt.isPresent()) {
                candidateResumeRepository.markPreviousAsNonCurrent(candidateId);
            }

            String cleanFilename = getCleanFilename(file.getOriginalFilename());
            Long newId = candidateResumeRepository.insert(
                    candidateId,
                    cleanFilename,
                    storageKey,
                    "application/pdf",
                    file.getSize()
            );

            // Clean up previous physical file on replacement
            if (previousOpt.isPresent()) {
                deletePhysicalFileSilently(resolveStoragePath(uploadPath, previousOpt.get().storageKey()));
            }

            ResumeEntity newResume = candidateResumeRepository.findByIdAndCandidateId(newId, candidateId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resume not found after upload."));

            return toResponse(newResume);
        } catch (Exception ex) {
            // Clean up the newly written file if database operation fails
            deletePhysicalFileSilently(targetFile);
            throw ex;
        }
    }

    public ResumeResponse getCurrentResume() {
        return getCurrentResume(SecurityUtils.getRequiredCandidateId());
    }

    public ResumeResponse getCurrentResume(Long candidateId) {
        if (candidateId == null) {
            throw new UnauthorizedException("Candidate identity required.");
        }

        ResumeEntity resume = candidateResumeRepository.findCurrentByCandidateId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No current resume found for candidate."));

        return toResponse(resume);
    }

    public void deleteCurrentResume() {
        deleteCurrentResume(SecurityUtils.getRequiredCandidateId());
    }

    @Transactional
    public void deleteCurrentResume(Long candidateId) {
        if (candidateId == null) {
            throw new UnauthorizedException("Candidate identity required.");
        }

        ResumeEntity current = candidateResumeRepository.findCurrentByCandidateId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No current resume found to delete."));

        candidateResumeRepository.deleteByIdAndCandidateId(current.id(), candidateId);

        try {
            Path uploadPath = getUploadDirectory();
            deletePhysicalFileSilently(resolveStoragePath(uploadPath, current.storageKey()));
        } catch (Exception e) {
            log.warn("Failed to delete physical file for resume {}: {}", current.storageKey(), e.getMessage());
        }
    }

    public record ResumeFileResource(
            String filename,
            String contentType,
            long sizeBytes,
            byte[] content
    ) {}

    public ResumeFileResource getResumeFile(String storageKey, String originalFilename, String contentType) {
        if (!StringUtils.hasText(storageKey)) {
            throw new ResourceNotFoundException("Resume file not found.");
        }
        try {
            Path uploadPath = getUploadDirectory();
            Path targetFile = resolveStoragePath(uploadPath, storageKey);
            if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
                log.warn("Physical resume file not found at path: {}", targetFile);
                throw new ResourceNotFoundException("Resume file not found on disk.");
            }
            byte[] bytes = Files.readAllBytes(targetFile);
            String cleanName = getCleanFilename(originalFilename);
            String mimeType = StringUtils.hasText(contentType) ? contentType : "application/pdf";
            return new ResumeFileResource(cleanName, mimeType, bytes.length, bytes);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (SecurityException e) {
            log.error("Security violation resolving resume path: {}", e.getMessage());
            throw new ResourceNotFoundException("Resume file could not be accessed.");
        } catch (Exception e) {
            log.error("Failed to read resume file from storage", e);
            throw new ResourceNotFoundException("Failed to read resume file.");
        }
    }

    public ResumeFileResource getCurrentResumeFile(Long candidateId) {
        if (candidateId == null) {
            throw new UnauthorizedException("Candidate identity required.");
        }

        ResumeEntity resume = candidateResumeRepository.findCurrentByCandidateId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No current resume found for candidate."));

        return getResumeFile(resume.storageKey(), resume.originalFilename(), resume.contentType());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Resume file is required and cannot be empty.", ErrorCode.CANDIDATE_RESUME_REQUIRED);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("File size exceeds maximum allowed size of 5 MiB.", ErrorCode.RESUME_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType.trim())) {
            throw new ValidationException("Invalid file type. Only application/pdf files are permitted.", ErrorCode.RESUME_INVALID_TYPE);
        }

        validatePdfMagicBytes(file);
    }

    private void validatePdfMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[PDF_MAGIC_BYTES.length];
            int bytesRead = is.read(header);
            if (bytesRead < PDF_MAGIC_BYTES.length) {
                throw new ValidationException("Invalid PDF file structure.", ErrorCode.RESUME_INVALID_TYPE);
            }

            for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
                if (header[i] != PDF_MAGIC_BYTES[i]) {
                    throw new ValidationException("Invalid file format. File does not contain valid PDF magic bytes.", ErrorCode.RESUME_INVALID_TYPE);
                }
            }
        } catch (BaseApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Failed to inspect file contents.", ErrorCode.RESUME_INVALID_TYPE);
        }
    }

    private Path getUploadDirectory() throws IOException {
        Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    private Path resolveStoragePath(Path baseDir, String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new SecurityException("Storage key cannot be empty.");
        }
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new SecurityException("Path traversal attempt detected in storage key: " + storageKey);
        }
        return resolved;
    }

    private void deletePhysicalFileSilently(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", path, e);
        }
    }

    private String getCleanFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "resume.pdf";
        }
        String clean = Paths.get(originalFilename).getFileName().toString();
        clean = clean.replace("\0", "").replaceAll("[/\\\\]", "").trim();
        return StringUtils.hasText(clean) ? clean : "resume.pdf";
    }

    private ResumeResponse toResponse(ResumeEntity entity) {
        return new ResumeResponse(
                entity.id(),
                entity.originalFilename(),
                entity.contentType(),
                entity.sizeBytes(),
                entity.isCurrent(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}
