package com.suplab.aether.agents.harness;

import com.agentharness.ConfidenceGate;
import com.agentharness.Harness;
import com.agentharness.ToolRegistry;
import com.agentharness.adapters.InMemoryKillSwitch;
import com.agentharness.ports.AuditPort;
import com.agentharness.ports.HumanReviewPort;
import com.agentharness.ports.ObservabilityPort;

/**
 * Wires an agent-harness {@link Harness} for Grid. The harness contributes the confidence gate and the
 * decision envelope; Grid keeps its own audit ({@code AgentDecisionEvent}), review
 * ({@code OrchestrationResult}) and metrics (Micrometer in the orchestrator), so the harness's
 * cross-cutting ports are no-ops here.
 */
public final class HarnessSupport {

    private HarnessSupport() {
    }

    /** A harness with its own (empty, default-deny) tool registry. */
    public static Harness governanceHarness() {
        return harness(new ToolRegistry());
    }

    /** A harness backed by a shared, pre-configured tool registry (the governance boundary for tools). */
    public static Harness harness(ToolRegistry registry) {
        return new Harness(registry, new NoopAudit(), new NoopHumanReview(),
                new NoopObservability(), new InMemoryKillSwitch(), new ConfidenceGate());
    }

    private static final class NoopAudit implements AuditPort {
        @Override
        public void record(AuditEntry entry) {
        }

        @Override
        public void recordSecurityEvent(SecurityEvent event) {
        }
    }

    private static final class NoopHumanReview implements HumanReviewPort {
        @Override
        public void enqueue(ReviewItem item) {
        }
    }

    private static final class NoopObservability implements ObservabilityPort {
        @Override
        public void emit(InvocationMetric metric) {
        }

        @Override
        public void incrementCounter(String name, int value) {
        }
    }
}
