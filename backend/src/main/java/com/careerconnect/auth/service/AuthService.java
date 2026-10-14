package com.careerconnect.auth.service;

import com.careerconnect.auth.dto.AuthResponse;
import com.careerconnect.auth.dto.AuthUserDto;
import com.careerconnect.auth.dto.LoginRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.auth.model.User;
import com.careerconnect.auth.repository.CandidateAuthRepository;
import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.auth.repository.UserRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ConflictException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ForbiddenException;
import com.careerconnect.common.exception.UnauthorizedException;
import com.careerconnect.common.model.Role;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final CandidateAuthRepository candidateAuthRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            CandidateAuthRepository candidateAuthRepository,
            RecruiterAuthRepository recruiterAuthRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.candidateAuthRepository = candidateAuthRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() == Role.ADMIN) {
            throw new BusinessRuleException(
                    "Administrative accounts cannot be registered publicly.",
                    ErrorCode.VALIDATION_FAILED
            );
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException(
                    "An account with this email address already exists.",
                    ErrorCode.EMAIL_ALREADY_EXISTS
            );
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = User.createNew(normalizedEmail, passwordHash, request.getRole());
        Long userId = userRepository.save(user);

        String trimmedName = request.getFullName().trim();
        Long candidateId = null;
        Long recruiterId = null;

        if (request.getRole() == Role.CANDIDATE) {
            candidateId = candidateAuthRepository.save(userId, trimmedName);
            log.info("Registered candidate: userId={}, candidateId={}, email={}", userId, candidateId, normalizedEmail);
        } else if (request.getRole() == Role.RECRUITER) {
            recruiterId = recruiterAuthRepository.save(userId, trimmedName);
            log.info("Registered recruiter: userId={}, recruiterId={}, email={}", userId, recruiterId, normalizedEmail);
        }

        SecurityUser securityUser = new SecurityUser(
                userId,
                normalizedEmail,
                passwordHash,
                request.getRole(),
                trimmedName,
                candidateId,
                recruiterId,
                null,
                true
        );

        String token = jwtService.generateToken(securityUser);
        Instant expiresAt = jwtService.getExpirationTimestamp(token);

        AuthUserDto authUserDto = buildAuthUserDto(userId, normalizedEmail, request.getRole(), trimmedName, candidateId, recruiterId, null);

        return AuthResponse.of(token, expiresAt, authUserDto);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password.", ErrorCode.INVALID_CREDENTIALS));

        if (!user.isActive()) {
            throw new ForbiddenException("Your account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", normalizedEmail);
            throw new UnauthorizedException("Invalid email or password.", ErrorCode.INVALID_CREDENTIALS);
        }

        String fullName = "Administrator";
        Long candidateId = null;
        Long recruiterId = null;
        Long companyId = null;

        if (user.getRole() == Role.CANDIDATE) {
            var candidateData = candidateAuthRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Candidate profile record missing for user " + user.getId()));
            candidateId = candidateData.candidateId();
            fullName = candidateData.fullName();
        } else if (user.getRole() == Role.RECRUITER) {
            var recruiterData = recruiterAuthRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Recruiter profile record missing for user " + user.getId()));
            recruiterId = recruiterData.recruiterId();
            companyId = recruiterData.companyId();
            fullName = recruiterData.fullName();
        }

        SecurityUser securityUser = new SecurityUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                fullName,
                candidateId,
                recruiterId,
                companyId,
                user.isActive()
        );

        String token = jwtService.generateToken(securityUser);
        Instant expiresAt = jwtService.getExpirationTimestamp(token);

        AuthUserDto authUserDto = buildAuthUserDto(user.getId(), user.getEmail(), user.getRole(), fullName, candidateId, recruiterId, companyId);

        log.info("Successful login for user: userId={}, role={}, email={}", user.getId(), user.getRole(), user.getEmail());
        return AuthResponse.of(token, expiresAt, authUserDto);
    }

    public AuthUserDto getCurrentUser(SecurityUser securityUser) {
        User user = userRepository.findById(securityUser.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User account not found."));

        if (!user.isActive()) {
            throw new ForbiddenException("Your account has been deactivated.");
        }

        String fullName = securityUser.getFullName();
        Long candidateId = null;
        Long recruiterId = null;
        Long companyId = null;

        if (user.getRole() == Role.CANDIDATE) {
            var candidateData = candidateAuthRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Candidate profile record missing for user " + user.getId()));
            candidateId = candidateData.candidateId();
            fullName = candidateData.fullName();
        } else if (user.getRole() == Role.RECRUITER) {
            var recruiterData = recruiterAuthRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Recruiter profile record missing for user " + user.getId()));
            recruiterId = recruiterData.recruiterId();
            companyId = recruiterData.companyId();
            fullName = recruiterData.fullName();
        }

        return buildAuthUserDto(user.getId(), user.getEmail(), user.getRole(), fullName, candidateId, recruiterId, companyId);
    }

    private AuthUserDto buildAuthUserDto(Long userId, String email, Role role, String fullName, Long candidateId, Long recruiterId, Long companyId) {
        if (role == Role.CANDIDATE) {
            return AuthUserDto.forCandidate(userId, email, fullName, candidateId);
        } else if (role == Role.RECRUITER) {
            return AuthUserDto.forRecruiter(userId, email, fullName, recruiterId, companyId);
        } else {
            return AuthUserDto.forAdmin(userId, email, fullName);
        }
    }
}
