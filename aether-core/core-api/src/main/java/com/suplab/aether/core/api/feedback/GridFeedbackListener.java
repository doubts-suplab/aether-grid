package com.suplab.aether.core.api.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.domain.AgentDecisionFeedback;
import com.suplab.aether.core.domain.DecisionOutcome;
import com.suplab.aether.core.memory.feedback.AgentDecisionFeedbackProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.Instant;

/**
 * Consumes Aether Grid decision feedback from the {@code aether.core.feedback} topic.
 *
 * <p>Message format (flat JSON, mirroring the REST contract — no shared DTO module):</p>
 * <pre>{@code
 * {
 *   "tenantId": "acme-corp",
 *   "userId": "user-42",
 *   "agentType": "GovernanceAgent",
 *   "decisionSummary": "Approved elevated API quota for analytics batch job",
 *   "outcome": "CORRECT",
 *   "confidence": 0.91,
 *   "engagementSignal": 0.8,          // optional — omit when not observed
 *   "occurredAt": "2026-07-10T08:00:00Z"  // optional — defaults to now
 * }
 * }</pre>
 *
 * <p>Malformed messages are logged and skipped — a bad event must never wedge the
 * consumer group. Only active when {@code aether.core.feedback.enabled=true}.</p>
 */
public class GridFeedbackListener {

    private static final Logger log = LoggerFactory.getLogger(GridFeedbackListener.class);

    private final AgentDecisionFeedbackProcessor processor;
    private final ObjectMapper objectMapper;

    public GridFeedbackListener(AgentDecisionFeedbackProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${aether.core.feedback.topic:aether.core.feedback}",
            groupId = "${aether.core.feedback.group-id:aether-core}")
    public void onMessage(String message) {
        AgentDecisionFeedback feedback;
        try {
            feedback = parse(message);
        } catch (Exception e) {
            log.warn("Skipping malformed feedback message ({}): {}", e.getMessage(), truncate(message));
            return;
        }
        processor.process(feedback);
    }

    /**
     * Parses the flat JSON document into the domain record. Field-by-field parsing keeps
     * error messages precise and applies contract defaults (engagementSignal=-1 when
     * absent, occurredAt=now).
     */
    AgentDecisionFeedback parse(String message) throws Exception {
        JsonNode node = objectMapper.readTree(message);
        var engagement = node.hasNonNull("engagementSignal")
                ? node.get("engagementSignal").asDouble() : -1.0;
        var occurredAt = node.hasNonNull("occurredAt")
                ? Instant.parse(node.get("occurredAt").asText()) : Instant.now();
        return new AgentDecisionFeedback(
                requiredText(node, "tenantId"),
                requiredText(node, "userId"),
                requiredText(node, "agentType"),
                requiredText(node, "decisionSummary"),
                DecisionOutcome.valueOf(requiredText(node, "outcome").toUpperCase()),
                node.hasNonNull("confidence") ? node.get("confidence").asDouble() : 0.0,
                engagement,
                occurredAt
        );
    }

    private static String requiredText(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new IllegalArgumentException("missing field: " + field);
        }
        return node.get(field).asText();
    }

    private static String truncate(String message) {
        return message.length() <= 200 ? message : message.substring(0, 200) + "…";
    }
}
