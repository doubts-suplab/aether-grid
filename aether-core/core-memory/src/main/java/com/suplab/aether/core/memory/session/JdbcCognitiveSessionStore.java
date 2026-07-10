package com.suplab.aether.core.memory.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.SessionStatus;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link CognitiveSessionStore} backed by the
 * {@code cognitive_sessions} table.
 *
 * <p>Turn summaries are stored as a JSONB array. A partial unique index enforces at most
 * one ACTIVE session per (tenant, user) — {@link #save(CognitiveSession)} closes any other
 * active session first so inserting a new active session never violates the constraint.</p>
 */
public class JdbcCognitiveSessionStore implements CognitiveSessionStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcCognitiveSessionStore.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcCognitiveSessionStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(CognitiveSession session) {
        if (session.isActive()) {
            closeOtherActiveSessions(session);
        }
        var sql = """
                INSERT INTO cognitive_sessions
                    (session_id, user_id, tenant_id, turn_summaries, emotional_state,
                     engagement_score, status, started_at, last_active_at)
                VALUES
                    (:sessionId, :userId, :tenantId, :turnSummaries::jsonb, :emotionalState,
                     :engagementScore, :status, :startedAt, :lastActiveAt)
                ON CONFLICT (session_id) DO UPDATE SET
                    turn_summaries = EXCLUDED.turn_summaries,
                    emotional_state = EXCLUDED.emotional_state,
                    engagement_score = EXCLUDED.engagement_score,
                    status = EXCLUDED.status,
                    last_active_at = EXCLUDED.last_active_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("sessionId", session.sessionId())
                .addValue("userId", session.userId())
                .addValue("tenantId", session.tenantId())
                .addValue("turnSummaries", toJson(session.turnSummaries()))
                .addValue("emotionalState", session.emotionalState())
                .addValue("engagementScore", session.engagementScore())
                .addValue("status", session.status().name())
                .addValue("startedAt", Timestamp.from(session.startedAt()))
                .addValue("lastActiveAt", Timestamp.from(session.lastActiveAt()));
        jdbc.update(sql, params);
        log.debug("Saved cognitive session id={} userId={} status={} turns={}",
                session.sessionId(), session.userId(), session.status(), session.turnSummaries().size());
    }

    private void closeOtherActiveSessions(CognitiveSession session) {
        var sql = """
                UPDATE cognitive_sessions
                SET status = 'CLOSED', last_active_at = NOW()
                WHERE tenant_id = :tenantId AND user_id = :userId
                  AND status = 'ACTIVE' AND session_id <> :sessionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", session.tenantId())
                .addValue("userId", session.userId())
                .addValue("sessionId", session.sessionId());
        int closed = jdbc.update(sql, params);
        if (closed > 0) {
            log.debug("Closed {} previous active session(s) for userId={} tenantId={}",
                    closed, session.userId(), session.tenantId());
        }
    }

    @Override
    public Optional<CognitiveSession> findById(UUID sessionId, String userId) {
        var sql = """
                SELECT session_id, user_id, tenant_id, turn_summaries, emotional_state,
                       engagement_score, status, started_at, last_active_at
                FROM cognitive_sessions
                WHERE session_id = :sessionId AND user_id = :userId
                """;
        var params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("userId", userId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public Optional<CognitiveSession> findActive(String tenantId, String userId) {
        var sql = """
                SELECT session_id, user_id, tenant_id, turn_summaries, emotional_state,
                       engagement_score, status, started_at, last_active_at
                FROM cognitive_sessions
                WHERE tenant_id = :tenantId AND user_id = :userId AND status = 'ACTIVE'
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<CognitiveSession> findByUser(String tenantId, String userId, int limit) {
        var sql = """
                SELECT session_id, user_id, tenant_id, turn_summaries, emotional_state,
                       engagement_score, status, started_at, last_active_at
                FROM cognitive_sessions
                WHERE tenant_id = :tenantId AND user_id = :userId
                ORDER BY last_active_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    private CognitiveSession mapRow(ResultSet rs, int row) throws SQLException {
        return new CognitiveSession(
                UUID.fromString(rs.getString("session_id")),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                fromJson(rs.getString("turn_summaries")),
                rs.getString("emotional_state"),
                rs.getDouble("engagement_score"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("last_active_at").toInstant()
        );
    }

    private String toJson(List<String> turnSummaries) {
        try {
            return objectMapper.writeValueAsString(turnSummaries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise turn summaries", e);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.warn("Malformed turn_summaries JSON, returning empty list: {}", e.getMessage());
            return List.of();
        }
    }
}
