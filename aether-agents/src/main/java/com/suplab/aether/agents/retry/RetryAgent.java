package com.suplab.aether.agents.retry;

import com.agentharness.Harness;
import com.suplab.aether.agents.harness.HarnessRouting;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.llm.LlmRequest;
import com.suplab.aether.agents.spi.Agent;
import com.suplab.aether.agents.spi.AgentCapability;
import com.suplab.aether.agents.spi.AgentDecision;
import com.suplab.aether.agents.spi.AgentInput;
import com.suplab.aether.agents.spi.AgentOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class RetryAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(RetryAgent.class);
    private static final String AGENT_TYPE = "RetryAgent";

    private static final String SYSTEM_PROMPT = """
            You are a retry strategy advisor. Analyse the failed API call and historical patterns.
            Return JSON: {"decision":"SUGGEST|DEFER|ALLOW","confidence":0.0-1.0,
            "rationale":"reason","retryAfterMs":0,"maxRetries":3}
            - SUGGEST: recommend a retry strategy
            - DEFER: delay retry until conditions improve
            - ALLOW: no retry needed
            Reply ONLY with JSON.
            """;

    private final LlmClient llmClient;
    private final Harness harness;

    public RetryAgent(LlmClient llmClient, Harness harness) {
        this.llmClient = llmClient;
        this.harness = harness;
    }

    @Override
    public String agentType() {
        return AGENT_TYPE;
    }

    @Override
    public Set<AgentCapability> capabilities() {
        return Set.of(AgentCapability.RETRY_STRATEGY);
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        var failureCount = countRecentFailures(input);
        if (failureCount == 0) {
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.ALLOW,
                    0.95, "No recent failures detected — no retry strategy needed",
                    Map.of("failureCount", 0));
        }

        var userPrompt = String.format(
                "Failed call: %s\nRecent failure count from memory: %d\nContext: %s",
                input.serialisedApiCall(), failureCount, input.context()
        );
        var request = LlmRequest.of("", SYSTEM_PROMPT, userPrompt);

        try {
            var response = llmClient.complete(request);
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.SUGGEST,
                    0.75, "Retry strategy derived from " + failureCount + " historical failures",
                    Map.of("rawLlmResponse", response.content(), "failureCount", failureCount));
        } catch (Exception e) {
            log.warn("RetryAgent LLM call failed: {}", e.getMessage());
            return HarnessRouting.gate(harness, AGENT_TYPE, input, AgentDecision.SUGGEST,
                    0.5, "LLM unavailable — suggesting exponential backoff default",
                    Map.of("retryAfterMs", 1000, "maxRetries", 3));
        }
    }

    private int countRecentFailures(AgentInput input) {
        return (int) input.relevantMemories().stream()
                .filter(m -> m.content().contains("FAILURE") || m.content().contains("TIMEOUT"))
                .count();
    }
}
