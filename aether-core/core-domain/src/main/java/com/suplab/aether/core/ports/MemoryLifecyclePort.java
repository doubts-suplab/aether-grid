package com.suplab.aether.core.ports;

/**
 * Port interface for the memory decay + archive lifecycle.
 *
 * <p>Mirrors human forgetting: memories that are not accessed lose strength over time
 * (retrieval reinforces them via {@code PersonalMemoryStore}), and memories that fade
 * below a threshold are archived — moved out of active recall, never silently deleted.</p>
 */
public interface MemoryLifecyclePort {

    /**
     * Outcome of one lifecycle run.
     *
     * @param decayedCount   memories whose strength was reduced this run
     * @param archivedCount  memories moved to the archive this run
     * @param totalRemaining active memories remaining after the run
     */
    record LifecycleResult(long decayedCount, long archivedCount, long totalRemaining) {}

    /**
     * Runs one decay + archive cycle over all users' memories:
     * <ol>
     *   <li>Decay: {@code strength -= decayRate × days_since_access} for memories not
     *       accessed within the grace period (floored at 0).</li>
     *   <li>Archive: memories with {@code strength} below the archive threshold are moved
     *       to {@code personal_memories_archive}.</li>
     * </ol>
     */
    LifecycleResult runLifecycle();
}
