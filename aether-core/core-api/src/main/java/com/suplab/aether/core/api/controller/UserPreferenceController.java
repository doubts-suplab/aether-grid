package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.ports.UserPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read/replace operations for per-user preferences.
 *
 * <p>Preferences flow into {@code PersonalContext.preferences} and are surfaced to
 * Aether Grid agents so decisions can be tailored to individual working styles.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/preferences")
public class UserPreferenceController {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceController.class);

    private final UserPreferenceStore preferenceStore;

    public UserPreferenceController(UserPreferenceStore preferenceStore) {
        this.preferenceStore = preferenceStore;
    }

    /**
     * Returns the user's preferences (empty map when none stored).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@PathVariable String userId) {
        return ResponseEntity.ok(preferenceStore.find(userId));
    }

    /**
     * Replaces the user's preference map. An empty body clears all preferences.
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> put(
            @PathVariable String userId,
            @RequestBody Map<String, Object> preferences) {
        preferenceStore.save(userId, preferences);
        log.info("Replaced preferences for userId={} keys={}", userId, preferences.size());
        return ResponseEntity.ok(preferences);
    }
}
