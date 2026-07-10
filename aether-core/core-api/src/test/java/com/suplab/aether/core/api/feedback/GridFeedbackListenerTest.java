package com.suplab.aether.core.api.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.domain.AgentDecisionFeedback;
import com.suplab.aether.core.domain.DecisionOutcome;
import com.suplab.aether.core.memory.feedback.AgentDecisionFeedbackProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GridFeedbackListenerTest {

    @Mock
    private AgentDecisionFeedbackProcessor processor;

    private GridFeedbackListener listener;

    @BeforeEach
    void setUp() {
        listener = new GridFeedbackListener(processor, new ObjectMapper());
    }

    @Test
    void onMessage_validMessage_delegatesToProcessor() {
        listener.onMessage("""
                {
                  "tenantId": "acme-corp",
                  "userId": "user-42",
                  "agentType": "GovernanceAgent",
                  "decisionSummary": "Approved elevated API quota",
                  "outcome": "CORRECT",
                  "confidence": 0.91,
                  "engagementSignal": 0.8,
                  "occurredAt": "2026-07-10T08:00:00Z"
                }
                """);

        var captor = ArgumentCaptor.forClass(AgentDecisionFeedback.class);
        verify(processor).process(captor.capture());

        var feedback = captor.getValue();
        assertThat(feedback.tenantId()).isEqualTo("acme-corp");
        assertThat(feedback.userId()).isEqualTo("user-42");
        assertThat(feedback.agentType()).isEqualTo("GovernanceAgent");
        assertThat(feedback.outcome()).isEqualTo(DecisionOutcome.CORRECT);
        assertThat(feedback.confidence()).isEqualTo(0.91);
        assertThat(feedback.engagementSignal()).isEqualTo(0.8);
        assertThat(feedback.hasEngagementSignal()).isTrue();
        assertThat(feedback.occurredAt()).isEqualTo(Instant.parse("2026-07-10T08:00:00Z"));
    }

    @Test
    void onMessage_optionalFieldsAbsent_appliesDefaults() {
        listener.onMessage("""
                {
                  "tenantId": "acme-corp",
                  "userId": "user-42",
                  "agentType": "RetryAgent",
                  "decisionSummary": "Backed off retries",
                  "outcome": "incorrect",
                  "confidence": 0.85
                }
                """);

        var captor = ArgumentCaptor.forClass(AgentDecisionFeedback.class);
        verify(processor).process(captor.capture());

        var feedback = captor.getValue();
        assertThat(feedback.outcome()).isEqualTo(DecisionOutcome.INCORRECT);
        assertThat(feedback.hasEngagementSignal()).isFalse();
        assertThat(feedback.occurredAt()).isNotNull();
    }

    @Test
    void onMessage_malformedJson_skipsWithoutThrowing() {
        listener.onMessage("this is not json {");

        verify(processor, never()).process(any());
    }

    @Test
    void onMessage_missingRequiredField_skipsWithoutThrowing() {
        listener.onMessage("""
                {"tenantId": "acme", "userId": "user-1", "outcome": "CORRECT", "confidence": 0.9}
                """);

        verify(processor, never()).process(any());
    }

    @Test
    void onMessage_unknownOutcome_skipsWithoutThrowing() {
        listener.onMessage("""
                {"tenantId": "acme", "userId": "user-1", "agentType": "X",
                 "decisionSummary": "s", "outcome": "MAYBE", "confidence": 0.9}
                """);

        verify(processor, never()).process(any());
    }
}
