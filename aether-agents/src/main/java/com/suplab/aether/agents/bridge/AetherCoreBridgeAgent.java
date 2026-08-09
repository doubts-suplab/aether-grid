package com.suplab.aether.agents.bridge;

import com.suplab.agentharness.Harness;
import com.suplab.aether.agents.harness.HarnessRouting;
import com.suplab.aether.agents.spi.Agent;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.agents.spi.AgentOutput;
import com.suplab.aether.core.ports.PersonalContextPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AetherCoreBridgeAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AetherCoreBridgeAgent.class);
    private static final String AGENT_TYPE = "AetherCoreBridgeAgent";

    private final PersonalContextPort personalContextPort;
    private final Harness harness;

    public AetherCoreBridgeAgent(PersonalContextPort personalContextPort, Harness harness) {
        this.personalContextPort = personalContextPort;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.PERSONAL_CONTEXT_ENRICHMENT);
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        var rawUserId = input.context().get("userId");
        if (rawUserId == null || rawUserId.toString().isBlank()) {
            return allow(input, 0.5, "no userId in context — personal enrichment skipped", Map.of());
        }

        var userId = rawUserId.toString();

        try {
            var result = personalContextPort.fetchFor(input.tenantId(), userId);

            if (result.isEmpty()) {
                return allow(input, 0.6, "no personal context available for user", Map.of());
            }

            var ctx = result.get();
            var memories = String.join("\n", ctx.recentMemorySummaries());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("personalMemories", memories);
            metadata.put("userPreferences", ctx.preferences().toString());
            metadata.put("emotionalState", ctx.emotionalState());
            metadata.put("engagementScore", String.valueOf(ctx.engagementScore()));

            log.info("AetherCoreBridgeAgent: enriched context for tenant={} user={}",
                    input.tenantId(), userId);

            return allow(input, 0.9, "personal context enriched", Map.copyOf(metadata));

        } catch (Exception e) {
            log.warn("AetherCoreBridgeAgent: enrichment failed for tenant={} user={}: {}",
                    input.tenantId(), userId, e.getMessage());
            return allow(input, 0.5, "personal context enrichment unavailable", Map.of());
        }
    }

    private AgentOutput allow(AgentInput input, double confidence, String rationale, Map<String, Object> metadata) {
        return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.ALLOW, confidence, rationale, metadata);
    }
}
