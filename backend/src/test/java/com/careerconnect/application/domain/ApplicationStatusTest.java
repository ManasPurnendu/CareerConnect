package com.careerconnect.application.domain;

import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.application.service.ApplicationService;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusTest {

    // ─── A. Valid Transitions ─────────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-A1: APPLIED can transition to UNDER_REVIEW, SHORTLISTED, REJECTED, and WITHDRAWN")
    void applied_validTransitions() {
        assertThat(ApplicationStatus.APPLIED.isValidTransition(ApplicationStatus.UNDER_REVIEW)).isTrue();
        assertThat(ApplicationStatus.APPLIED.isValidTransition(ApplicationStatus.SHORTLISTED)).isTrue();
        assertThat(ApplicationStatus.APPLIED.isValidTransition(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.APPLIED.isValidTransition(ApplicationStatus.WITHDRAWN)).isTrue();
        assertThat(ApplicationStatus.APPLIED.getAllowedNextStatuses())
                .containsExactlyInAnyOrder(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("P5.3-A2: UNDER_REVIEW can transition to SHORTLISTED, REJECTED, and WITHDRAWN")
    void underReview_validTransitions() {
        assertThat(ApplicationStatus.UNDER_REVIEW.isValidTransition(ApplicationStatus.SHORTLISTED)).isTrue();
        assertThat(ApplicationStatus.UNDER_REVIEW.isValidTransition(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.UNDER_REVIEW.isValidTransition(ApplicationStatus.WITHDRAWN)).isTrue();
        assertThat(ApplicationStatus.UNDER_REVIEW.getAllowedNextStatuses())
                .containsExactlyInAnyOrder(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("P5.3-A3: SHORTLISTED can transition to INTERVIEW, REJECTED, and WITHDRAWN")
    void shortlisted_validTransitions() {
        assertThat(ApplicationStatus.SHORTLISTED.isValidTransition(ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(ApplicationStatus.SHORTLISTED.isValidTransition(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.SHORTLISTED.isValidTransition(ApplicationStatus.WITHDRAWN)).isTrue();
        assertThat(ApplicationStatus.SHORTLISTED.getAllowedNextStatuses())
                .containsExactlyInAnyOrder(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("P5.3-A4: INTERVIEW can transition to SELECTED, REJECTED, and WITHDRAWN")
    void interview_validTransitions() {
        assertThat(ApplicationStatus.INTERVIEW.isValidTransition(ApplicationStatus.SELECTED)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.isValidTransition(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.isValidTransition(ApplicationStatus.WITHDRAWN)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.getAllowedNextStatuses())
                .containsExactlyInAnyOrder(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);
    }

    // ─── B. Invalid Transitions ───────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-B1: APPLIED cannot transition directly to SELECTED or INTERVIEW")
    void applied_invalidTransitions() {
        assertThat(ApplicationStatus.APPLIED.isValidTransition(ApplicationStatus.SELECTED)).isFalse();
        assertThat(ApplicationStatus.APPLIED.isValidTransition(ApplicationStatus.INTERVIEW)).isFalse();
    }

    @Test
    @DisplayName("P5.3-B2: UNDER_REVIEW cannot transition to SELECTED, INTERVIEW, or APPLIED")
    void underReview_invalidTransitions() {
        assertThat(ApplicationStatus.UNDER_REVIEW.isValidTransition(ApplicationStatus.SELECTED)).isFalse();
        assertThat(ApplicationStatus.UNDER_REVIEW.isValidTransition(ApplicationStatus.INTERVIEW)).isFalse();
        assertThat(ApplicationStatus.UNDER_REVIEW.isValidTransition(ApplicationStatus.APPLIED)).isFalse();
    }

    @Test
    @DisplayName("P5.3-B3: SHORTLISTED cannot transition to SELECTED, APPLIED, or UNDER_REVIEW")
    void shortlisted_invalidTransitions() {
        assertThat(ApplicationStatus.SHORTLISTED.isValidTransition(ApplicationStatus.SELECTED)).isFalse();
        assertThat(ApplicationStatus.SHORTLISTED.isValidTransition(ApplicationStatus.APPLIED)).isFalse();
        assertThat(ApplicationStatus.SHORTLISTED.isValidTransition(ApplicationStatus.UNDER_REVIEW)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("P5.3-B4: SELECTED cannot transition to any status (terminal)")
    void selected_hasNoTransitions(ApplicationStatus target) {
        assertThat(ApplicationStatus.SELECTED.isValidTransition(target)).isFalse();
        assertThat(ApplicationStatus.SELECTED.getAllowedNextStatuses()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("P5.3-B5: REJECTED cannot transition to any status (terminal)")
    void rejected_hasNoTransitions(ApplicationStatus target) {
        assertThat(ApplicationStatus.REJECTED.isValidTransition(target)).isFalse();
        assertThat(ApplicationStatus.REJECTED.getAllowedNextStatuses()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("P5.3-B6: WITHDRAWN cannot transition to any status (terminal)")
    void withdrawn_hasNoTransitions(ApplicationStatus target) {
        assertThat(ApplicationStatus.WITHDRAWN.isValidTransition(target)).isFalse();
        assertThat(ApplicationStatus.WITHDRAWN.getAllowedNextStatuses()).isEmpty();
    }

    // ─── C. Same-State Transitions ───────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("P5.3-C1: Same-state transitions are always rejected")
    void sameState_transitionsRejected(ApplicationStatus status) {
        assertThat(status.isValidTransition(status)).isFalse();
    }

    @Test
    @DisplayName("P5.3-C2: Null target is always rejected")
    void nullTarget_rejected() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(status.isValidTransition(null)).isFalse();
        }
    }

    // ─── D. Terminal & Withdrawable States ────────────────────────────────────

    @Test
    @DisplayName("P5.3-D1: Terminal states are exactly SELECTED, REJECTED, WITHDRAWN")
    void terminalStates_areExact() {
        Set<ApplicationStatus> terminal = Set.of(
                ApplicationStatus.SELECTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(status.isTerminal()).isEqualTo(terminal.contains(status));
        }
    }

    @Test
    @DisplayName("P5.3-D2: Withdrawable states are exactly APPLIED, UNDER_REVIEW, SHORTLISTED, INTERVIEW")
    void withdrawableStates_areExact() {
        Set<ApplicationStatus> withdrawable = Set.of(
                ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW,
                ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(status.isWithdrawable()).isEqualTo(withdrawable.contains(status));
        }
    }

    // ─── E. Service Transition Validation Exception ──────────────────────────

    @Test
    @DisplayName("P5.3-E1: validateTransition throws 422 INVALID_STATUS_TRANSITION on invalid transition")
    void service_validateTransition_throwsOnInvalid() {
        ApplicationService service = new ApplicationService(
                null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.SELECTED))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> {
                    BusinessRuleException bre = (BusinessRuleException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                });

        assertThatThrownBy(() -> service.validateTransition(ApplicationStatus.WITHDRAWN, ApplicationStatus.APPLIED))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> {
                    BusinessRuleException bre = (BusinessRuleException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                });

        assertThatThrownBy(() -> service.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.APPLIED))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> {
                    BusinessRuleException bre = (BusinessRuleException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                });
    }

    @Test
    @DisplayName("P5.3-E2: validateTransition succeeds without exception on valid transitions")
    void service_validateTransition_succeedsOnValid() {
        ApplicationService service = new ApplicationService(
                null, null, null, null, null, null, null, null, null);

        service.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW);
        service.validateTransition(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN);
        service.validateTransition(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED);
        service.validateTransition(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED);
        service.validateTransition(ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW);
        service.validateTransition(ApplicationStatus.INTERVIEW, ApplicationStatus.SELECTED);
    }
}
