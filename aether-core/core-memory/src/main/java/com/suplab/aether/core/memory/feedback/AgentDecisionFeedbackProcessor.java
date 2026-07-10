package com.suplab.aether.core.memory.feedback;

import com.suplab.aether.core.domain.AgentDecisionFeedback;
import com.suplab.aether.core.domain.DecisionOutcome;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Turns Aether Grid decision feedback into personal memories — the learning half of
 * the Grid ↔ Core loop.
 *
 * <p>Mapping rules:</p>
 * <ul>
 *   <li>{@link DecisionOutcome#CORRECT} → a PROCEDURAL memory is created ("what worked
 *       for this user"), so future context assembly surfaces proven approaches.</li>
 *   <li>{@link DecisionOutcome#INCORRECT} / {@link DecisionOutcome#OVERRIDDEN} → a
 *       PROCEDURAL memory recording what did NOT work, stored at reduced strength so it
 *       fades unless the pattern repeats.</li>
 *   <li>An engagement signal → an EPISODIC-style EMOTIONAL memory reflecting the user's
 *       engagement level (ENGAGED ≥ 0.66, NEUTRAL ≥ 0.33, DISENGAGED below).</li>
 * </ul>
 *
 * <p>The embedding service is optional — when absent, memories are stored with zero
 * vectors and remain retrievable by type.</p>
 */
public class AgentDecisionFeedbackProcessor {

    private static final Logger log = LoggerFactory.getLogger(AgentDecisionFeedbackProcessor.class);
    private static final int EMBEDDING_DIM = 384;
    private static final double NEGATIVE_OUTCOME_STRENGTH = 0.6;

    private final PersonalMemoryStore memoryStore;
    private final Optional<PersonalEmbeddingService> embeddingService;

    public AgentDecisionFeedbackProcessor(PersonalMemoryStore memoryStore,
                                          Optional<PersonalEmbeddingService> embeddingService) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Processes one feedback event: creates a PROCEDURAL memory from the decision outcome
     * and, when an engagement signal is present, an EMOTIONAL memory.
     */
    public void process(AgentDecisionFeedback feedback) {
        storeProceduralMemory(feedback);
        if (feedback.hasEngagementSignal()) {
            storeEmotionalMemory(feedback);
        }
        log.info("Processed Grid feedback userId={} agentType={} outcome={} engagementSignal={}",
                feedback.userId(), feedback.agentType(), feedback.outcome(),
                feedback.hasEngagementSignal() ? feedback.engagementSignal() : "none");
    }

    private void storeProceduralMemory(AgentDecisionFeedback feedback) {
        var correct = feedback.outcome() == DecisionOutcome.CORRECT;
        var content = correct
                ? "%s decision worked for this user: %s".formatted(feedback.agentType(), feedback.decisionSummary())
                : "%s decision did NOT work for this user (%s): %s".formatted(
                        feedback.agentType(), feedback.outcome().name().toLowerCase(), feedback.decisionSummary());

        var memory = PersonalMemory.create(feedback.userId(), MemoryType.PROCEDURAL, content);
        if (!correct) {
            memory = new PersonalMemory(memory.id(), memory.userId(), memory.type(), memory.content(),
                    NEGATIVE_OUTCOME_STRENGTH, 0, memory.createdAt(), memory.lastAccessedAt());
        }
        memoryStore.save(memory, embed(content));
        log.debug("Stored PROCEDURAL memory id={} userId={} outcome={}",
                memory.id(), feedback.userId(), feedback.outcome());
    }

    private void storeEmotionalMemory(AgentDecisionFeedback feedback) {
        var state = engagementToEmotionalState(feedback.engagementSignal());
        var memory = PersonalMemory.create(feedback.userId(), MemoryType.EMOTIONAL, state);
        memoryStore.save(memory, embed(state));
        log.debug("Stored EMOTIONAL memory id={} userId={} state={}",
                memory.id(), feedback.userId(), state);
    }

    static String engagementToEmotionalState(double signal) {
        if (signal >= 0.66) return "ENGAGED";
        if (signal >= 0.33) return "NEUTRAL";
        return "DISENGAGED";
    }

    private float[] embed(String content) {
        return embeddingService.map(svc -> svc.embed(content))
                .orElseGet(() -> new float[EMBEDDING_DIM]);
    }
}
