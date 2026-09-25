package dev.infrai.creator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationDecisionTest {
    @Test void flaggedSubmissionIsQuarantinedAndKeepsReviewReasons() {
        PublicationDecision decision = PublicationDecision.from(true, List.of("policy_review"));
        assertThat(decision.state()).isEqualTo(PublicationDecision.State.QUARANTINED);
        assertThat(decision.reasons()).containsExactly("policy_review");
    }

    @Test void cleanSubmissionCanReachSubscribers() {
        PublicationDecision decision = PublicationDecision.from(false, List.of());
        assertThat(decision.state()).isEqualTo(PublicationDecision.State.READY_FOR_SUBSCRIBERS);
        assertThat(decision.reasons()).isEmpty();
    }
}
