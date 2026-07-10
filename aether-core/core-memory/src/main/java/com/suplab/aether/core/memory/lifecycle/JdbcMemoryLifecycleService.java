package com.suplab.aether.core.memory.lifecycle;

import com.suplab.aether.core.ports.MemoryLifecyclePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Set-based JDBC implementation of {@link MemoryLifecyclePort}.
 *
 * <p>Both steps run as single SQL statements — no per-row round trips, so a run over
 * millions of memories stays cheap. The archive step uses a data-modifying CTE
 * ({@code WITH moved AS (DELETE ... RETURNING ...) INSERT ...}) so move-to-archive is
 * atomic: a memory is never deleted without landing in the archive, and never duplicated.</p>
 */
public class JdbcMemoryLifecycleService implements MemoryLifecyclePort {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryLifecycleService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final double decayRate;
    private final int decayAfterDays;
    private final double archiveThreshold;

    /**
     * @param decayRate        strength lost per day since last access (default 0.01)
     * @param decayAfterDays   grace period — memories accessed within this many days don't decay
     * @param archiveThreshold memories below this strength are archived (default 0.1)
     */
    public JdbcMemoryLifecycleService(NamedParameterJdbcTemplate jdbc,
                                      double decayRate,
                                      int decayAfterDays,
                                      double archiveThreshold) {
        this.jdbc = jdbc;
        this.decayRate = decayRate;
        this.decayAfterDays = decayAfterDays;
        this.archiveThreshold = archiveThreshold;
    }

    @Override
    public LifecycleResult runLifecycle() {
        long decayed = decay();
        long archived = archive();
        long remaining = countActive();
        log.info("Memory lifecycle run complete: decayed={} archived={} totalRemaining={}",
                decayed, archived, remaining);
        return new LifecycleResult(decayed, archived, remaining);
    }

    private long decay() {
        var sql = """
                UPDATE personal_memories
                SET strength = GREATEST(0,
                        strength - :decayRate * (EXTRACT(EPOCH FROM (NOW() - last_accessed_at)) / 86400.0))
                WHERE last_accessed_at < NOW() - make_interval(days => :decayAfterDays)
                """;
        var params = new MapSqlParameterSource()
                .addValue("decayRate", decayRate)
                .addValue("decayAfterDays", decayAfterDays);
        return jdbc.update(sql, params);
    }

    private long archive() {
        var sql = """
                WITH moved AS (
                    DELETE FROM personal_memories
                    WHERE strength < :threshold
                    RETURNING id, user_id, memory_type, content, embedding, strength,
                              access_count, created_at, last_accessed_at
                )
                INSERT INTO personal_memories_archive
                    (id, user_id, memory_type, content, embedding, strength,
                     access_count, created_at, last_accessed_at, archived_at)
                SELECT id, user_id, memory_type, content, embedding, strength,
                       access_count, created_at, last_accessed_at, NOW()
                FROM moved
                """;
        return jdbc.update(sql, new MapSqlParameterSource("threshold", archiveThreshold));
    }

    private long countActive() {
        var sql = "SELECT COUNT(*) FROM personal_memories";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count != null ? count : 0L;
    }
}
