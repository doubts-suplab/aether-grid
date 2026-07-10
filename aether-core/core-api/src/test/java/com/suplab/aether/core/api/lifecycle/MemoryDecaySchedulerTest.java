package com.suplab.aether.core.api.lifecycle;

import com.suplab.aether.core.ports.MemoryLifecyclePort;
import com.suplab.aether.core.ports.MemoryLifecyclePort.LifecycleResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryDecaySchedulerTest {

    @Mock
    private MemoryLifecyclePort lifecycle;

    private SimpleMeterRegistry meterRegistry;
    private MemoryDecayScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new MemoryDecayScheduler(lifecycle, meterRegistry);
    }

    @Test
    void runDecayCycle_delegatesAndRecordsMetrics() {
        when(lifecycle.runLifecycle()).thenReturn(new LifecycleResult(12, 3, 100));

        scheduler.runDecayCycle();

        verify(lifecycle).runLifecycle();
        assertThat(meterRegistry.counter("aether.core.memories.decayed").count()).isEqualTo(12.0);
        assertThat(meterRegistry.counter("aether.core.memories.archived").count()).isEqualTo(3.0);
        assertThat(meterRegistry.get("aether.core.memories.total").gauge().value()).isEqualTo(100.0);
    }

    @Test
    void runDecayCycle_countersAccumulateAcrossRuns_gaugeReflectsLatest() {
        when(lifecycle.runLifecycle())
                .thenReturn(new LifecycleResult(10, 2, 90))
                .thenReturn(new LifecycleResult(5, 1, 84));

        scheduler.runDecayCycle();
        scheduler.runDecayCycle();

        assertThat(meterRegistry.counter("aether.core.memories.decayed").count()).isEqualTo(15.0);
        assertThat(meterRegistry.counter("aether.core.memories.archived").count()).isEqualTo(3.0);
        assertThat(meterRegistry.get("aether.core.memories.total").gauge().value()).isEqualTo(84.0);
    }
}
