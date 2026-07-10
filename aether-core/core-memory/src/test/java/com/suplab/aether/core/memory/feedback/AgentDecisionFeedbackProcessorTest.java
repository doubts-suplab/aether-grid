package com.suplab.aether.core.memory.feedback;

import com.suplab.aether.core.domain.AgentDecisionFeedback;
import com.suplab.aether.core.domain.DecisionOutcome;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentDecisionFeedbackProcessorTest {

    @Mock
    private PersonalMemoryStore memoryStore;

    private AgentDecisionFeedbackProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AgentDecisionFeedbackProcessor(memoryStore, Optional.empty());
    }

    @Test
    void process_correctOutcome_createsFullStrengthProceduralMemory() {
        processor.process(feedback(DecisionOutcome.CORRECT, -1.0));

        var captor = ArgumentCaptor.forClass(PersonalMemory.class);
        verify(memoryStore).save(captor.capture(), any(float[].class));

        var memory = captor.getValue();
        assertThat(memory.type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(memory.userId()).isEqualTo("user-42");
        assertThat(memory.content()).contains("GovernanceAgent decision worked", "Approved elevated quota");
        assertThat(memory.strength()).isEqualTo(1.0);
    }

    @Test
    void process_incorrectOutcome_createsReducedStrengthProceduralMemory() {
        processor.process(feedback(DecisionOutcome.INCORRECT, -1.0));

        var captor = ArgumentCaptor.forClass(PersonalMemory.class);
        verify(memoryStore).save(captor.capture(), any(float[].class));

        var memory = captor.getValue();
        assertThat(memory.type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(memory.content()).contains("did NOT work", "incorrect");
        assertThat(memory.strength()).isEqualTo(0.6);
    }

    @Test
    void process_overriddenOutcome_recordsOverrideInContent() {
        processor.process(feedback(DecisionOutcome.OVERRIDDEN, -1.0));

        var captor = ArgumentCaptor.forClass(PersonalMemory.class);
        verify(memoryStore).save(captor.capture(), any(float[].class));

        assertThat(captor.getValue().content()).contains("overridden");
    }

    @Test
    void process_withEngagementSignal_alsoCreatesEmotionalMemory() {
        processor.process(feedback(DecisionOutcome.CORRECT, 0.8));

        var captor = ArgumentCaptor.forClass(PersonalMemory.class);
        verify(memoryStore, times(2)).save(captor.capture(), any(float[].class));

        List<PersonalMemory> saved = captor.getAllValues();
        assertThat(saved).extracting(PersonalMemory::type)
                .containsExactly(MemoryType.PROCEDURAL, MemoryType.EMOTIONAL);
        assertThat(saved.get(1).content()).isEqualTo("ENGAGED");
    }

    @Test
    void process_withoutEngagementSignal_createsOnlyProceduralMemory() {
        processor.process(feedback(DecisionOutcome.CORRECT, -1.0));

        verify(memoryStore, times(1)).save(any(PersonalMemory.class), any(float[].class));
    }

    @Test
    void engagementToEmotionalState_mapsBands() {
        assertThat(AgentDecisionFeedbackProcessor.engagementToEmotionalState(0.9)).isEqualTo("ENGAGED");
        assertThat(AgentDecisionFeedbackProcessor.engagementToEmotionalState(0.66)).isEqualTo("ENGAGED");
        assertThat(AgentDecisionFeedbackProcessor.engagementToEmotionalState(0.5)).isEqualTo("NEUTRAL");
        assertThat(AgentDecisionFeedbackProcessor.engagementToEmotionalState(0.33)).isEqualTo("NEUTRAL");
        assertThat(AgentDecisionFeedbackProcessor.engagementToEmotionalState(0.1)).isEqualTo("DISENGAGED");
    }

    private static AgentDecisionFeedback feedback(DecisionOutcome outcome, double engagementSignal) {
        return new AgentDecisionFeedback("acme", "user-42", "GovernanceAgent",
                "Approved elevated quota", outcome, 0.9, engagementSignal, Instant.now());
    }
}
