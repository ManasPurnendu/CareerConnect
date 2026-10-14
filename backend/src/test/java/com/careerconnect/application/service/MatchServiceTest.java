package com.careerconnect.application.service;

import com.careerconnect.application.dto.JobMatchDto;
import com.careerconnect.candidate.repository.CandidateSkillRepository;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.repository.JobRepository;
import com.careerconnect.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchServiceTest {

    private MatchService matchService;

    @BeforeEach
    void setUp() {
        JobRepository jobRepository = new JobRepository(null) {
            @Override
            public boolean existsJobById(Long id) {
                return id != null && id.equals(100L);
            }
            @Override
            public List<String> findRequiredSkillNamesByJobId(Long jobId) {
                return List.of("Java", "SQL");
            }
        };
        CandidateSkillRepository candidateSkillRepository = new CandidateSkillRepository(null);
        matchService = new MatchService(jobRepository, candidateSkillRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. 100% match when candidate possesses all required skills")
    void testFullMatch() {
        List<String> jobSkills = List.of("Java", "SQL", "Spring Boot");
        List<String> candidateSkills = List.of("Java", "SQL", "Spring Boot");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("Java", "SQL", "Spring Boot");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("2. 0% match when candidate possesses none of the required skills")
    void testZeroMatch() {
        List<String> jobSkills = List.of("Java", "SQL", "Spring Boot");
        List<String> candidateSkills = List.of("Python", "Go", "Docker");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(0);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).containsExactly("Java", "SQL", "Spring Boot");
        assertThat(result.additionalSkills()).containsExactly("Python", "Go", "Docker");
    }

    @Test
    @DisplayName("3. Partial match calculates correct percentage and skill sets")
    void testPartialMatch() {
        List<String> jobSkills = List.of("Java", "SQL", "Spring Boot");
        List<String> candidateSkills = List.of("java", "Python", "sql");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        // 2 matched out of 3 = round(200 / 3) = 67%
        assertThat(result.percentage()).isEqualTo(67);
        assertThat(result.matchedSkills()).containsExactly("Java", "SQL");
        assertThat(result.missingSkills()).containsExactly("Spring Boot");
        assertThat(result.additionalSkills()).containsExactly("Python");
    }

    @Test
    @DisplayName("4. Missing skills accurately populated")
    void testMissingSkills() {
        List<String> jobSkills = List.of("Java", "SQL", "Spring Boot", "Kubernetes");
        List<String> candidateSkills = List.of("Java", "SQL");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.matchedSkills()).containsExactly("Java", "SQL");
        assertThat(result.missingSkills()).containsExactly("Spring Boot", "Kubernetes");
        assertThat(result.percentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("5. Additional skills accurately populated and do not increase percentage")
    void testAdditionalSkills() {
        List<String> jobSkills = List.of("Java", "SQL");
        List<String> candidateSkills = List.of("Java", "SQL", "Python", "Docker", "AWS");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("Java", "SQL");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).containsExactly("Python", "Docker", "AWS");
    }

    @Test
    @DisplayName("6. Candidate has no skills: 0% match and all job skills missing")
    void testCandidateHasNoSkills() {
        List<String> jobSkills = List.of("Java", "SQL");
        List<String> candidateSkills = List.of();

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(0);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).containsExactly("Java", "SQL");
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("7. Job has no required skills: 100% match and candidate skills are additional")
    void testJobHasNoRequiredSkills() {
        List<String> jobSkills = List.of();
        List<String> candidateSkills = List.of("Java", "Python");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).containsExactly("Java", "Python");
    }

    @Test
    @DisplayName("8. Both candidate and job have no skills: 100% match and all lists empty")
    void testBothEmpty() {
        JobMatchDto result = matchService.calculateMatch(List.of(), List.of());

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("9. Case normalization treats 'JAVA', 'Java', and 'java' as equivalent")
    void testCaseNormalization() {
        List<String> jobSkills = List.of("JAVA", "SQL");
        List<String> candidateSkills = List.of("java", "sql");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("JAVA", "SQL");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("10. Whitespace normalization treats ' java ' and 'java' as equivalent")
    void testWhitespaceNormalization() {
        List<String> jobSkills = List.of("  Spring Boot  ", "  Java");
        List<String> candidateSkills = List.of("spring boot", "java  ");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("Spring Boot", "Java");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("11. Duplicate candidate skills do not count multiple times")
    void testDuplicateCandidateSkills() {
        List<String> jobSkills = List.of("Java", "Python");
        List<String> candidateSkills = List.of("Java", "java", "JAVA", "Java ");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        // 1 matched out of 2 = 50%
        assertThat(result.percentage()).isEqualTo(50);
        assertThat(result.matchedSkills()).containsExactly("Java");
        assertThat(result.missingSkills()).containsExactly("Python");
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("12. Duplicate job required skills do not inflate total required skills count")
    void testDuplicateRequiredSkills() {
        List<String> jobSkills = List.of("Java", "JAVA", " java ");
        List<String> candidateSkills = List.of("java");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        // Total required normalized distinct skills = 1, matched = 1 -> 100%
        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("Java");
        assertThat(result.missingSkills()).isEmpty();
    }

    @Test
    @DisplayName("13. Mixed-case and whitespace duplicates handled correctly")
    void testMixedCaseAndWhitespaceDuplicates() {
        List<String> jobSkills = List.of(" Spring Boot ", "spring boot", "SPRING BOOT");
        List<String> candidateSkills = List.of("  Spring Boot  ");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("Spring Boot");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).isEmpty();
    }

    @Test
    @DisplayName("14-18. Exact percentage rounding ratios")
    void testPercentageRoundingRatios() {
        // 1 of 1: 100%
        JobMatchDto r1 = matchService.calculateMatch(List.of("A"), List.of("A"));
        assertThat(r1.percentage()).isEqualTo(100);

        // 1 of 2: 50%
        JobMatchDto r2 = matchService.calculateMatch(List.of("A", "B"), List.of("A"));
        assertThat(r2.percentage()).isEqualTo(50);

        // 2 of 3: 67%
        JobMatchDto r3 = matchService.calculateMatch(List.of("A", "B", "C"), List.of("A", "B"));
        assertThat(r3.percentage()).isEqualTo(67);

        // 3 of 4: 75%
        JobMatchDto r4 = matchService.calculateMatch(List.of("A", "B", "C", "D"), List.of("A", "B", "C"));
        assertThat(r4.percentage()).isEqualTo(75);

        // 1 of 3: 33% (round(100/3) = 33)
        JobMatchDto r5 = matchService.calculateMatch(List.of("A", "B", "C"), List.of("A"));
        assertThat(r5.percentage()).isEqualTo(33);

        // 1 of 6: 17% (round(100/6) = 17)
        JobMatchDto r6 = matchService.calculateMatch(List.of("A", "B", "C", "D", "E", "F"), List.of("A"));
        assertThat(r6.percentage()).isEqualTo(17);

        // 5 of 6: 83% (round(500/6) = 83)
        JobMatchDto r7 = matchService.calculateMatch(List.of("A", "B", "C", "D", "E", "F"), List.of("A", "B", "C", "D", "E"));
        assertThat(r7.percentage()).isEqualTo(83);
    }

    @Test
    @DisplayName("19. Extra candidate skills do not increase score beyond 100%")
    void testExtraCandidateSkillsDoNotInflatePercentage() {
        List<String> jobSkills = List.of("Java");
        List<String> candidateSkills = List.of("Java", "Go", "Rust", "Python", "C++", "Docker", "Kubernetes");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).containsExactly("Java");
        assertThat(result.additionalSkills()).hasSize(6);
    }

    @Test
    @DisplayName("20. Missing required skills reduce percentage")
    void testMissingRequiredSkillsReducePercentage() {
        List<String> jobSkills = List.of("Java", "SQL", "Docker", "AWS");
        List<String> candidateSkills = List.of("Java", "SQL", "Docker");

        JobMatchDto result = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(result.percentage()).isEqualTo(75);
        assertThat(result.missingSkills()).containsExactly("AWS");
    }

    @Test
    @DisplayName("21. Job not found throws ResourceNotFoundException with JOB_NOT_FOUND code")
    void testJobNotFoundThrows() {
        SecurityUser user = new SecurityUser(
                1L, "candidate@example.com", "hash", Role.CANDIDATE, "Test Candidate",
                10L, null, null, true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        assertThatThrownBy(() -> matchService.calculateJobMatch(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999 not found")
                .satisfies(ex -> {
                    ResourceNotFoundException rne = (ResourceNotFoundException) ex;
                    assertThat(rne.getErrorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("24. Determinism: identical inputs produce identical outputs across repeated invocations")
    void testDeterminism() {
        List<String> jobSkills = List.of("Java", "Spring Boot", "SQL", "AWS");
        List<String> candidateSkills = List.of("aws", "java", "Docker", "GraphQL");

        JobMatchDto run1 = matchService.calculateMatch(jobSkills, candidateSkills);
        JobMatchDto run2 = matchService.calculateMatch(jobSkills, candidateSkills);

        assertThat(run1).isEqualTo(run2);
        assertThat(run1.percentage()).isEqualTo(run2.percentage());
        assertThat(run1.matchedSkills()).isEqualTo(run2.matchedSkills());
        assertThat(run1.missingSkills()).isEqualTo(run2.missingSkills());
        assertThat(run1.additionalSkills()).isEqualTo(run2.additionalSkills());
    }

    @Test
    @DisplayName("Null safety: null lists treated as empty lists without error")
    void testNullSafety() {
        JobMatchDto result = matchService.calculateMatch(null, null);

        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.additionalSkills()).isEmpty();
    }
}
