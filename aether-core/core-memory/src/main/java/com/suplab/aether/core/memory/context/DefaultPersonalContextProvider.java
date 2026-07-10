package com.suplab.aether.core.memory.context;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalContext;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.PersonalContextProvider;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles a {@link PersonalContext} snapshot from stored personal memories, the user's
 * active cognitive session, and stored preferences.
 *
 * <p>Priority of cognitive state signals: an ACTIVE session's emotional state and
 * engagement score override memory-derived values — the live session is the freshest
 * signal. Session turn summaries are prepended to the memory summaries so Grid agents
 * see the current conversation first.</p>
 *
 * <p>Returns {@link Optional#empty()} when the user has no memories, no active session,
 * and no preferences — callers (e.g. {@code PersonalContextController} and Aether Grid)
 * treat this as a no-context signal and proceed with defaults.</p>
 */
public class DefaultPersonalContextProvider implements PersonalContextProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonalContextProvider.class);

    private final PersonalMemoryStore memoryStore;
    private final CognitiveSessionStore sessionStore;
    private final UserPreferenceStore preferenceStore;
    private final int defaultMemoryLimit;

    public DefaultPersonalContextProvider(PersonalMemoryStore memoryStore,
                                          CognitiveSessionStore sessionStore,
                                          UserPreferenceStore preferenceStore,
                                          int defaultMemoryLimit) {
        this.memoryStore = memoryStore;
        this.sessionStore = sessionStore;
        this.preferenceStore = preferenceStore;
        this.defaultMemoryLimit = defaultMemoryLimit;
    }

    @Override
    public Optional<PersonalContext> buildContext(String tenantId, String userId) {
        var episodic = memoryStore.findByType(userId, MemoryType.EPISODIC, defaultMemoryLimit);
        var semantic  = memoryStore.findByType(userId, MemoryType.SEMANTIC,  defaultMemoryLimit);
        var emotional = memoryStore.findByType(userId, MemoryType.EMOTIONAL, 2);
        var activeSession = sessionStore.findActive(tenantId, userId);
        var preferences = preferenceStore.find(userId);

        if (episodic.isEmpty() && semantic.isEmpty() && emotional.isEmpty()
                && activeSession.isEmpty() && preferences.isEmpty()) {
            log.debug("No cognitive data for userId={} tenantId={} — returning empty context", userId, tenantId);
            return Optional.empty();
        }

        List<String> summaries = new ArrayList<>();
        activeSession.ifPresent(s -> summaries.addAll(s.turnSummaries()));
        episodic.forEach(m -> summaries.add(m.content()));
        semantic.forEach(m  -> summaries.add(m.content()));

        var emotionalState = activeSession
                .map(CognitiveSession::emotionalState)
                .orElseGet(() -> emotional.isEmpty()
                        ? "NEUTRAL"
                        : emotional.getFirst().content().toUpperCase());

        var engagementScore = activeSession
                .map(CognitiveSession::engagementScore)
                .orElseGet(() -> episodic.isEmpty()
                        ? 0.5
                        : episodic.stream().mapToDouble(m -> m.strength()).average().orElse(0.5));

        var context = new PersonalContext(
                userId,
                tenantId,
                summaries,
                preferences,
                emotionalState,
                Math.min(1.0, engagementScore),
                Instant.now()
        );

        log.debug("Built personal context userId={} tenantId={} summaries={} emotionalState={} activeSession={}",
                userId, tenantId, summaries.size(), emotionalState, activeSession.isPresent());
        return Optional.of(context);
    }
}
