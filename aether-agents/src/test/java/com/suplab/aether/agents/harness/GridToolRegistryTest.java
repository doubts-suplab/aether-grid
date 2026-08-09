package com.suplab.aether.agents.harness;

import com.suplab.agentharness.Agent;
import com.suplab.agentharness.ConfidenceGate;
import com.suplab.agentharness.Harness;
import com.suplab.agentharness.ToolInvoker;
import com.suplab.agentharness.ToolRegistry;
import com.suplab.agentharness.adapters.InMemoryAudit;
import com.suplab.agentharness.adapters.InMemoryHumanReview;
import com.suplab.agentharness.adapters.InMemoryKillSwitch;
import com.suplab.agentharness.adapters.InMemoryObservability;
import com.suplab.agentharness.model.AgentInput;
import com.suplab.agentharness.model.AuthorityLevel;
import com.suplab.agentharness.model.Decision;
import com.suplab.agentharness.model.DecisionAction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool registry wired into Grid's harness is the governance boundary for
 * tools: default-deny, and a
 * refused call becomes a safe failure default plus a security event. An agent
 * may only reach a tool it has
 * been explicitly granted.
 */
class GridToolRegistryTest {

    private static final AgentInput REQ = new AgentInput("t", "u", Map.of(), Map.of());

    /** A minimal harness agent that calls the policy_lookup tool. */
    private record ToolAgent(String name) implements Agent {
        @Override
        public AuthorityLevel authorityLevel() {
            return AuthorityLevel.BLOCK;
        }

        @Override
        public Set<DecisionAction> capabilities() {
            return Set.of(DecisionAction.ALLOW, DecisionAction.DEFER);
        }

        @Override
        public Decision decide(AgentInput input, ToolInvoker tools) {
            Object result = tools.call("policy_lookup", Map.of());
            return Decision.propose(DecisionAction.ALLOW, 0.99, "policy: " + result);
        }
    }

    private Harness harnessWith(ToolRegistry registry, InMemoryAudit audit) {
        return new Harness(registry, audit, new InMemoryHumanReview(),
                new InMemoryObservability(), new InMemoryKillSwitch(), new ConfidenceGate());
    }

    private ToolRegistry registryWithPolicyLookup() {
        var registry = new ToolRegistry();
        registry.registerTool("policy_lookup", "read", args -> "no-matching-policy");
        return registry;
    }

    @Test
    void grantedAgentCanCallTool() {
        var registry = registryWithPolicyLookup();
        registry.grant("granted-agent", Set.of("policy_lookup"));
        var audit = new InMemoryAudit();

        var out = harnessWith(registry, audit).invoke(new ToolAgent("granted-agent"), REQ);

        assertThat(out.decision().action()).isEqualTo(DecisionAction.ALLOW);
        assertThat(out.decision().rationale()).contains("no-matching-policy");
        assertThat(audit.securityEvents()).isEmpty();
    }

    @Test
    void ungrantedAgentIsRefusedByDefaultDeny() {
        var registry = registryWithPolicyLookup(); // no grant → default-deny
        var audit = new InMemoryAudit();

        var out = harnessWith(registry, audit).invoke(new ToolAgent("denied-agent"), REQ);

        assertThat(out.decision().action()).isEqualTo(DecisionAction.DEFER); // safe failure default
        assertThat(audit.securityEvents()).anyMatch(e -> e.kind().equals("tool_not_authorized"));
    }
}
