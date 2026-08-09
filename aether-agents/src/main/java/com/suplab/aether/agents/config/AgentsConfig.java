package com.suplab.aether.agents.config;

import com.suplab.agentharness.Harness;
import com.suplab.agentharness.ToolRegistry;
import com.suplab.aether.agents.bridge.AetherCoreBridgeAgent;
import com.suplab.aether.agents.bridge.AetherCoreProperties;
import com.suplab.aether.agents.governance.GovernanceAgent;
import com.suplab.aether.agents.hallucination.HallucinationDetectorAgent;
import com.suplab.aether.agents.harness.HarnessSupport;
import com.suplab.aether.agents.llm.LlmClient;
import com.suplab.aether.agents.orchestrator.AgentOrchestrator;
import com.suplab.aether.agents.reflection.ReflectionAgent;
import com.suplab.aether.agents.registry.AgentRegistry;
import com.suplab.aether.agents.retry.RetryAgent;
import com.suplab.aether.agents.selfimproving.SelfImprovingAgent;
import com.suplab.aether.agents.spi.Agent;
import com.suplab.aether.agents.temporal.TemporalPredictionAgent;
import com.suplab.aether.core.ports.AgentFeedbackPort;
import com.suplab.aether.core.ports.PersonalContextPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

@Configuration
@Import(com.suplab.aether.agents.llm.LlmClientConfig.class)
@EnableConfigurationProperties(AetherCoreProperties.class)
public class AgentsConfig {

    /**
     * The shared tool registry — the governance boundary for agent tool use
     * (default-deny, no wildcards).
     * A demonstrative read-only {@code policy_lookup} tool is registered and
     * granted only to the
     * GovernanceAgent; no other agent can reach it. Wire real tools (policy/memory
     * lookups) here.
     */
    @Bean
    public ToolRegistry agentToolRegistry() {
        var registry = new ToolRegistry();
        registry.registerTool("policy_lookup", "read", args -> "no-matching-policy");
        registry.grant("GovernanceAgent", Set.of("policy_lookup"));
        return registry;
    }

    /**
     * The shared agent-harness. Every agent routes its decision through this for
     * the centralized gate.
     */
    @Bean
    public Harness agentHarness(ToolRegistry agentToolRegistry) {
        return HarnessSupport.harness(agentToolRegistry);
    }

    @Bean
    public GovernanceAgent governanceAgent(LlmClient llmClient, Harness agentHarness) {
        return new GovernanceAgent(llmClient, agentHarness);
    }

    @Bean
    public RetryAgent retryAgent(LlmClient llmClient, Harness agentHarness) {
        return new RetryAgent(llmClient, agentHarness);
    }

    @Bean
    public HallucinationDetectorAgent hallucinationDetectorAgent(LlmClient llmClient, Harness agentHarness) {
        return new HallucinationDetectorAgent(llmClient, agentHarness);
    }

    @Bean
    public TemporalPredictionAgent temporalPredictionAgent(LlmClient llmClient, Harness agentHarness) {
        return new TemporalPredictionAgent(llmClient, agentHarness);
    }

    @Bean
    public ReflectionAgent reflectionAgent(LlmClient llmClient, Harness agentHarness) {
        return new ReflectionAgent(llmClient, agentHarness);
    }

    @Bean
    public SelfImprovingAgent selfImprovingAgent(LlmClient llmClient, AgentFeedbackPort feedbackPort,
            Harness agentHarness) {
        return new SelfImprovingAgent(llmClient, feedbackPort, agentHarness);
    }

    @Bean
    public AetherCoreBridgeAgent aetherCoreBridgeAgent(PersonalContextPort personalContextPort,
            Harness agentHarness) {
        return new AetherCoreBridgeAgent(personalContextPort, agentHarness);
    }

    @Bean
    public AgentRegistry agentRegistry(List<Agent> agents) {
        return new AgentRegistry(agents);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator(AgentRegistry agentRegistry, MeterRegistry meterRegistry) {
        return new AgentOrchestrator(agentRegistry, meterRegistry);
    }
}
