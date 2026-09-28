package com.emanstagram.story;

import com.emanstagram.user.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Hourly housekeeping.
 *
 * <p>Expired stories are already hidden by every query, so this only reclaims
 * storage. Refresh tokens are kept for a week past expiry so reuse detection
 * still recognises a recently rotated token, then deleted.
 */
@Component
public class StoryCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StoryCleanupJob.class);
    private static final Duration TOKEN_GRACE = Duration.ofDays(7);

    private final StoryService stories;
    private final RefreshTokenRepository refreshTokens;

    public StoryCleanupJob(StoryService stories, RefreshTokenRepository refreshTokens) {
        this.stories = stories;
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    public void purgeExpiredStories() {
        int removed = stories.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired stories", removed);
        }
    }

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT6H")
    @Transactional
    public void purgeDeadRefreshTokens() {
        int removed = refreshTokens.deleteExpiredBefore(Instant.now().minus(TOKEN_GRACE));
        if (removed > 0) {
            log.info("Deleted {} long-expired refresh tokens", removed);
        }
    }
}
