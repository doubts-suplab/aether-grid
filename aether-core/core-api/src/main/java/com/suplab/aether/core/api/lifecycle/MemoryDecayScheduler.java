package com.suplab.aether.core.api.lifecycle;

import com.suplab.aether.core.ports.MemoryLifecyclePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Nightly memory lifecycle job — decays unused memories and archives faded ones.
 *
 * <p>Default schedule is 03:00 daily ({@code aether.core.memory.decay-cron}). Exposed
 * metrics:</p>
 * <ul>
 *   <li>{@code aether.core.memories.total} — gauge, active memories after the last run</li>
 *   <li>{@code aether.core.memories.decayed} — counter, memories weakened across runs</li>
 *   <li>{@code aether.core.memories.archived} — counter, memories archived across runs</li>
 * </ul>
 */
public class MemoryDecayScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayScheduler.class);

    private final MemoryLifecyclePort lifecycle;
    private final Counter decayedCounter;
    private final Counter archivedCounter;
    private final AtomicLong totalMemories = new AtomicLong(0);

    public MemoryDecayScheduler(MemoryLifecyclePort lifecycle, MeterRegistry meterRegistry) {
        this.lifecycle = lifecycle;
        this.decayedCounter = Counter.builder("aether.core.memories.decayed")
                .description("Memories weakened by the decay job")
                .register(meterRegistry);
        this.archivedCounter = Counter.builder("aether.core.memories.archived")
                .description("Memories moved to the archive by the decay job")
                .register(meterRegistry);
        Gauge.builder("aether.core.memories.total", totalMemories, AtomicLong::get)
                .description("Active personal memories after the last lifecycle run")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${aether.core.memory.decay-cron:0 0 3 * * *}")
    public void runDecayCycle() {
        log.debug("Starting scheduled memory lifecycle run");
        var result = lifecycle.runLifecycle();
        decayedCounter.increment(result.decayedCount());
        archivedCounter.increment(result.archivedCount());
        totalMemories.set(result.totalRemaining());
    }
}
