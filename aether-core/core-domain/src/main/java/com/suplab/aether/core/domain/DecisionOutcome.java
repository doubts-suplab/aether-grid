package com.suplab.aether.core.domain;

/**
 * Outcome of an Aether Grid agent decision, as reported back to Core via the
 * {@code aether.core.feedback} Kafka topic.
 */
public enum DecisionOutcome {
    /** The agent's decision was validated as correct (auto or by a human reviewer). */
    CORRECT,
    /** The agent's decision was wrong. */
    INCORRECT,
    /** A human overrode the agent's decision. */
    OVERRIDDEN
}
