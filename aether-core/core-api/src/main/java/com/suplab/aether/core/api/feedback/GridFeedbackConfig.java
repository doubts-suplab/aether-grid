package com.suplab.aether.core.api.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.memory.feedback.AgentDecisionFeedbackProcessor;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Wires the Grid feedback loop — Kafka consumer plus feedback processor.
 *
 * <p>Disabled by default: Core must run standalone without Kafka or Grid present.
 * Enable with {@code aether.core.feedback.enabled=true} (env {@code FEEDBACK_ENABLED})
 * and point {@code spring.kafka.bootstrap-servers} at the broker.</p>
 */
@Configuration
@ConditionalOnProperty(name = "aether.core.feedback.enabled", havingValue = "true")
public class GridFeedbackConfig {

    @Bean
    public AgentDecisionFeedbackProcessor agentDecisionFeedbackProcessor(
            PersonalMemoryStore memoryStore,
            Optional<PersonalEmbeddingService> embeddingService) {
        return new AgentDecisionFeedbackProcessor(memoryStore, embeddingService);
    }

    @Bean
    public GridFeedbackListener gridFeedbackListener(AgentDecisionFeedbackProcessor processor,
                                                     ObjectMapper objectMapper) {
        return new GridFeedbackListener(processor, objectMapper);
    }
}
