package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.CognitiveSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for persisting multi-turn cognitive sessions.
 *
 * <p>All lookups are scoped by userId (and tenantId where applicable) — implementations
 * must never return another user's session.</p>
 */
public interface CognitiveSessionStore {

    /**
     * Inserts or updates a session. Saving an ACTIVE session closes any other ACTIVE
     * session the user has in the same tenant — a user holds at most one active session
     * per tenant.
     */
    void save(CognitiveSession session);

    /**
     * Finds a session by its ID, scoped to the owning user.
     */
    Optional<CognitiveSession> findById(UUID sessionId, String userId);

    /**
     * Finds the user's currently ACTIVE session in the given tenant, if any.
     */
    Optional<CognitiveSession> findActive(String tenantId, String userId);

    /**
     * Returns the user's sessions in the tenant, most recently active first.
     */
    List<CognitiveSession> findByUser(String tenantId, String userId, int limit);
}
