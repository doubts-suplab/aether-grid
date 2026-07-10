package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lifecycle operations for multi-turn cognitive sessions.
 *
 * <p>Creating a session closes any previous ACTIVE session for the same user/tenant —
 * a user holds exactly one active session per tenant. Turns are appended via PATCH and
 * update the session's emotional state and engagement score, which then flow into the
 * {@code PersonalContext} served to Aether Grid.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/users/{userId}/sessions")
public class CognitiveSessionController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveSessionController.class);

    private final CognitiveSessionStore sessionStore;

    public CognitiveSessionController(CognitiveSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Starts a new ACTIVE session for the user, closing any prior active session.
     *
     * @return 201 Created with the new session
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String tenantId,
            @PathVariable String userId) {
        var session = CognitiveSession.start(tenantId, userId);
        sessionStore.save(session);
        log.info("Started cognitive session id={} userId={} tenantId={}",
                session.sessionId(), userId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toBody(session));
    }

    /**
     * Lists the user's sessions, most recently active first.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        var sessions = sessionStore.findByUser(tenantId, userId, limit);
        return ResponseEntity.ok(sessions.stream().map(CognitiveSessionController::toBody).toList());
    }

    /**
     * Returns a single session by ID, scoped to the owning user.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @PathVariable UUID sessionId) {
        return sessionStore.findById(sessionId, userId)
                .map(session -> ResponseEntity.ok(toBody(session)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Appends a turn to the session.
     *
     * <p>Request body: {@code {"turnSummary": "...", "emotionalState": "FOCUSED",
     * "engagementScore": 0.8}} — only {@code turnSummary} is required.</p>
     *
     * @return 200 OK with the updated session, 404 if not found, 409 if the session is closed
     */
    @PatchMapping("/{sessionId}/turns")
    public ResponseEntity<Map<String, Object>> addTurn(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> body) {

        var turnSummary = body.get("turnSummary") instanceof String s ? s : null;
        if (turnSummary == null || turnSummary.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "turnSummary is required"));
        }
        var emotionalState = body.get("emotionalState") instanceof String s ? s : null;
        var engagementScore = body.get("engagementScore") instanceof Number n ? n.doubleValue() : -1.0;

        var existing = sessionStore.findById(sessionId, userId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.get().isActive()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "session is closed"));
        }

        var updated = existing.get().withTurn(turnSummary, emotionalState, engagementScore);
        sessionStore.save(updated);
        log.debug("Added turn to session id={} userId={} turns={}",
                sessionId, userId, updated.turnSummaries().size());
        return ResponseEntity.ok(toBody(updated));
    }

    /**
     * Closes the session. Closing an already-closed session is a no-op.
     *
     * @return 200 OK with the closed session, 404 if not found
     */
    @PostMapping("/{sessionId}/close")
    public ResponseEntity<Map<String, Object>> close(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @PathVariable UUID sessionId) {
        var existing = sessionStore.findById(sessionId, userId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var closed = existing.get().close();
        sessionStore.save(closed);
        log.info("Closed cognitive session id={} userId={}", sessionId, userId);
        return ResponseEntity.ok(toBody(closed));
    }

    private static Map<String, Object> toBody(CognitiveSession session) {
        return Map.of(
                "sessionId", session.sessionId().toString(),
                "userId", session.userId(),
                "tenantId", session.tenantId(),
                "turnSummaries", session.turnSummaries(),
                "emotionalState", session.emotionalState(),
                "engagementScore", session.engagementScore(),
                "status", session.status().name(),
                "startedAt", session.startedAt().toString(),
                "lastActiveAt", session.lastActiveAt().toString()
        );
    }
}
