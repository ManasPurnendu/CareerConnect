package com.careerconnect.security;

import com.careerconnect.auth.model.User;
import com.careerconnect.auth.repository.CandidateAuthRepository;
import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.auth.repository.UserRepository;
import com.careerconnect.common.model.Role;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final CandidateAuthRepository candidateAuthRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            CandidateAuthRepository candidateAuthRepository,
            RecruiterAuthRepository recruiterAuthRepository) {
        this.userRepository = userRepository;
        this.candidateAuthRepository = candidateAuthRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        String fullName = "User";
        Long candidateId = null;
        Long recruiterId = null;
        Long companyId = null;

        if (user.getRole() == Role.CANDIDATE) {
            var data = candidateAuthRepository.findByUserId(user.getId());
            if (data.isPresent()) {
                candidateId = data.get().candidateId();
                fullName = data.get().fullName();
            }
        } else if (user.getRole() == Role.RECRUITER) {
            var data = recruiterAuthRepository.findByUserId(user.getId());
            if (data.isPresent()) {
                recruiterId = data.get().recruiterId();
                companyId = data.get().companyId();
                fullName = data.get().fullName();
            }
        } else if (user.getRole() == Role.ADMIN) {
            fullName = "Administrator";
        }

        return new SecurityUser(
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
    }
}
