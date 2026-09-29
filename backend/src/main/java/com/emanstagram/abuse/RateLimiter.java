package com.emanstagram.abuse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter: "at most {@code limit} hits per {@code window}
 * for this key".
 *
 * <p>In memory, which is right for a single instance. Counters reset when the
 * server restarts; the storage quota (the limit that protects the bucket)
 * lives in the database and does not. Running several instances would move
 * this to Redis.
 */
@Component
public class RateLimiter {

    /** Keys idle longer than the longest window are dropped by {@link #evictIdle()}. */
    private static final long IDLE_MILLIS = Duration.ofHours(2).toMillis();

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Records a hit if allowed.
     *
     * @return 0 when allowed, otherwise the seconds until a slot frees up
     */
    public long tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= now - windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                long retryMillis = deque.peekFirst() + windowMillis - now;
                return Math.max(1, (retryMillis + 999) / 1000);
            }
            deque.addLast(now);
            return 0;
        }
    }

    /** Forgets every counter. Used by tests. */
    public void reset() {
        hits.clear();
    }

    @Scheduled(fixedDelayString = "PT10M")
    void evictIdle() {
        long cutoff = System.currentTimeMillis() - IDLE_MILLIS;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || last < cutoff;
            }
        });
    }
}
