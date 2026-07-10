package com.suplab.aether.core.domain;

/**
 * Lifecycle state of a {@link CognitiveSession}.
 *
 * <p>A user has at most one ACTIVE session per tenant at a time — enforced by the
 * session store, which closes prior sessions when a new one is created.</p>
 */
public enum SessionStatus {
    ACTIVE,
    CLOSED
}
