package com.suplab.aether.agents.config;

import com.suplab.aether.agents.bridge.AetherCoreBridgeAgent;
import com.suplab.aether.agents.bridge.AetherCoreProperties;
import com.agentharness.Harness;
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

@Configuration
@Import(com.suplab.aether.agents.llm.LlmClientConfig.class)
@EnableConfigurationProperties(AetherCoreProperties.class)
public class AgentsConfig {

    @Bean
    public Harness agentHarness() {
        return HarnessSupport.governanceHarness();
    }

    @Bean
    public GovernanceAgent governanceAgent(LlmClient llmClient, Harness agentHarness) {
        return new GovernanceAgent(llmClient, agentHarness);
    }

    @Bean
    public RetryAgent retryAgent(LlmClient llmClient) {
        return new RetryAgent(llmClient);
    }

    @Bean
    public HallucinationDetectorAgent hallucinationDetectorAgent(LlmClient llmClient) {
        return new HallucinationDetectorAgent(llmClient);
    }

    @Bean
    public TemporalPredictionAgent temporalPredictionAgent(LlmClient llmClient) {
        return new TemporalPredictionAgent(llmClient);
    }

    @Bean
    public ReflectionAgent reflectionAgent(LlmClient llmClient) {
        return new ReflectionAgent(llmClient);
    }

    @Bean
    public SelfImprovingAgent selfImprovingAgent(LlmClient llmClient, AgentFeedbackPort feedbackPort) {
        return new SelfImprovingAgent(llmClient, feedbackPort);
    }

    @Bean
    public AetherCoreBridgeAgent aetherCoreBridgeAgent(PersonalContextPort personalContextPort) {
        return new AetherCoreBridgeAgent(personalContextPort);
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
