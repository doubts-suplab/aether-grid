package com.suplab.aether.core.memory.context;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultPersonalContextProviderTest {

    @Mock
    private PersonalMemoryStore memoryStore;
    @Mock
    private CognitiveSessionStore sessionStore;
    @Mock
    private UserPreferenceStore preferenceStore;

    private DefaultPersonalContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultPersonalContextProvider(memoryStore, sessionStore, preferenceStore, 5);
        // Default: no data anywhere; individual tests override what they need
        when(memoryStore.findByType(anyString(), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(anyString(), eq(MemoryType.SEMANTIC), anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(anyString(), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of());
        when(sessionStore.findActive(anyString(), anyString())).thenReturn(Optional.empty());
        when(preferenceStore.find(anyString())).thenReturn(Map.of());
    }

    @Test
    void buildContext_returnsEmptyWhenNoDataAtAll() {
        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void buildContext_includesEpisodicAndSemanticInSummaries() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt()))
                .thenReturn(List.of(memory("user-1", MemoryType.EPISODIC, "Presented Q3 roadmap")));
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.SEMANTIC), anyInt()))
                .thenReturn(List.of(memory("user-1", MemoryType.SEMANTIC, "Prefers async communication")));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().recentMemorySummaries())
                .containsExactly("Presented Q3 roadmap", "Prefers async communication");
    }

    @Test
    void buildContext_setsEmotionalStateFromFirstEmotionalMemory() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt()))
                .thenReturn(List.of(memory("user-1", MemoryType.EMOTIONAL, "motivated")));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().emotionalState()).isEqualTo("MOTIVATED");
    }

    @Test
    void buildContext_defaultsToNeutralWithNoEmotionalMemories() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt()))
                .thenReturn(List.of(memory("user-1", MemoryType.EPISODIC, "some event")));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().emotionalState()).isEqualTo("NEUTRAL");
    }

    @Test
    void buildContext_computesEngagementScoreFromEpisodicStrengths() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt()))
                .thenReturn(List.of(
                        memoryWithStrength("user-1", MemoryType.EPISODIC, "event A", 0.8),
                        memoryWithStrength("user-1", MemoryType.EPISODIC, "event B", 0.6)));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().engagementScore()).isCloseTo(0.7, within(0.001));
    }

    @Test
    void buildContext_populatesUserAndTenantId() {
        when(memoryStore.findByType(eq("user-42"), eq(MemoryType.EPISODIC), anyInt()))
                .thenReturn(List.of(memory("user-42", MemoryType.EPISODIC, "meeting notes")));

        var result = provider.buildContext("corp-tenant", "user-42");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("user-42");
        assertThat(result.get().tenantId()).isEqualTo("corp-tenant");
    }

    @Test
    void buildContext_activeSessionOverridesMemoryDerivedState() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt()))
                .thenReturn(List.of(memory("user-1", MemoryType.EMOTIONAL, "tired")));
        var session = CognitiveSession.start("acme", "user-1")
                .withTurn("Reviewing deployment plan", "focused", 0.9);
        when(sessionStore.findActive("acme", "user-1")).thenReturn(Optional.of(session));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().emotionalState()).isEqualTo("FOCUSED");
        assertThat(result.get().engagementScore()).isCloseTo(0.9, within(0.001));
    }

    @Test
    void buildContext_sessionTurnsComeBeforeMemorySummaries() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt()))
                .thenReturn(List.of(memory("user-1", MemoryType.EPISODIC, "old event")));
        var session = CognitiveSession.start("acme", "user-1")
                .withTurn("current conversation turn", null, -1);
        when(sessionStore.findActive("acme", "user-1")).thenReturn(Optional.of(session));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().recentMemorySummaries())
                .containsExactly("current conversation turn", "old event");
    }

    @Test
    void buildContext_includesStoredPreferences() {
        when(preferenceStore.find("user-1"))
                .thenReturn(Map.of("communication-style", "async"));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().preferences()).containsEntry("communication-style", "async");
    }

    @Test
    void buildContext_nonEmptyWithOnlyActiveSession() {
        var session = CognitiveSession.start("acme", "user-1");
        when(sessionStore.findActive("acme", "user-1")).thenReturn(Optional.of(session));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().emotionalState()).isEqualTo("NEUTRAL");
    }

    private static PersonalMemory memory(String userId, MemoryType type, String content) {
        return memoryWithStrength(userId, type, content, 1.0);
    }

    private static PersonalMemory memoryWithStrength(String userId, MemoryType type, String content, double strength) {
        return new PersonalMemory(UUID.randomUUID(), userId, type, content,
                strength, 0, Instant.now(), Instant.now());
    }
}
