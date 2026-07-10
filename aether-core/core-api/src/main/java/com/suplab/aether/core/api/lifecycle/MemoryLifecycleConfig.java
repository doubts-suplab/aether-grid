package com.suplab.aether.core.api.lifecycle;

import com.suplab.aether.core.memory.lifecycle.JdbcMemoryLifecycleService;
import com.suplab.aether.core.ports.MemoryLifecyclePort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the memory decay + archive lifecycle.
 *
 * <p>Enabled by default; set {@code aether.core.memory.decay-enabled=false}
 * (env {@code MEMORY_DECAY_ENABLED}) to disable — e.g. in test environments.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aether.core.memory.decay-enabled", havingValue = "true", matchIfMissing = true)
public class MemoryLifecycleConfig {

    /**
     * Creates the set-based lifecycle service.
     *
     * @param decayRate        strength lost per day since last access
     * @param decayAfterDays   grace period before decay applies
     * @param archiveThreshold strength below which memories are archived
     */
    @Bean
    public MemoryLifecyclePort memoryLifecyclePort(
            NamedParameterJdbcTemplate jdbc,
            @Value("${aether.core.memory.decay-rate:0.01}") double decayRate,
            @Value("${aether.core.memory.decay-after-days:7}") int decayAfterDays,
            @Value("${aether.core.memory.archive-threshold:0.1}") double archiveThreshold) {
        return new JdbcMemoryLifecycleService(jdbc, decayRate, decayAfterDays, archiveThreshold);
    }

    @Bean
    public MemoryDecayScheduler memoryDecayScheduler(MemoryLifecyclePort lifecycle,
                                                     MeterRegistry meterRegistry) {
        return new MemoryDecayScheduler(lifecycle, meterRegistry);
    }
}
