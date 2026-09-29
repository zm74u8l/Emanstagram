package com.emanstagram.abuse;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import com.emanstagram.user.User;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops one account from filling the media bucket.
 *
 * <p>Two independent budgets, checked before a single byte is uploaded:
 * <ul>
 *   <li><b>Storage quota</b>: everything the account currently stores (posts,
 *       stories, chat attachments, avatar, banner). Totalled from the
 *       database on each upload, so it is exact and survives restarts.</li>
 *   <li><b>Daily upload budget</b>: bytes and files uploaded in the last 24
 *       hours, <i>including anything since deleted</i>. Without this, an
 *       upload-then-delete loop would stay under the quota forever while
 *       burning bandwidth. Accounts younger than {@code newAccountPeriod} get
 *       a smaller budget, which blunts throwaway sign-ups.</li>
 * </ul>
 * The daily ledger is in memory and resets on restart; the quota does not.
 */
@Component
public class UploadGuard {

    private static final Duration DAY = Duration.ofHours(24);

    private record Entry(long at, long bytes, int files) {
    }

    public record Usage(long usedBytes, long quotaBytes, long uploadedTodayBytes, long dailyLimitBytes,
                        int uploadsToday, int dailyLimitFiles, boolean newAccount) {
    }

    private final EntityManager em;
    private final EmanstagramProperties.Limits limits;
    private final Map<UUID, Deque<Entry>> ledger = new ConcurrentHashMap<>();

    public UploadGuard(EntityManager em, EmanstagramProperties properties) {
        this.em = em;
        this.limits = properties.limits();
    }

    /**
     * Throws if the upload would exceed a budget; otherwise records it
     * against today's budget. Call before uploading anything.
     */
    @Transactional(readOnly = true)
    public void admit(User user, long incomingBytes, int incomingFiles) {
        if (user.isSuspended()) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account has been suspended.");
        }
        if (limits == null) {
            return;
        }

        long used = storedBytes(user.getId(), user);
        if (used + incomingBytes > limits.storageQuotaBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STORAGE_QUOTA_EXCEEDED",
                    "You've used your %s of storage. Delete some posts to make room."
                            .formatted(human(limits.storageQuotaBytes())));
        }

        boolean fresh = isNew(user);
        long byteBudget = fresh ? limits.newAccountDailyUploadBytes() : limits.dailyUploadBytes();
        int fileBudget = fresh ? limits.newAccountDailyUploadCount() : limits.dailyUploadCount();

        Deque<Entry> entries = ledger.computeIfAbsent(user.getId(), k -> new ArrayDeque<>());
        synchronized (entries) {
            prune(entries);
            long bytesToday = entries.stream().mapToLong(Entry::bytes).sum();
            int filesToday = entries.stream().mapToInt(Entry::files).sum();
            if (filesToday + incomingFiles > fileBudget || bytesToday + incomingBytes > byteBudget) {
                String who = fresh ? "New accounts can" : "You can";
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DAILY_UPLOAD_LIMIT",
                        "%s upload up to %d files or %s a day. Please try again tomorrow."
                                .formatted(who, fileBudget, human(byteBudget)));
            }
            entries.addLast(new Entry(System.currentTimeMillis(), incomingBytes, incomingFiles));
        }
    }

    /** What the account has used, for the Settings screen and moderators. */
    @Transactional(readOnly = true)
    public Usage usage(User user) {
        long used = storedBytes(user.getId(), user);
        boolean fresh = isNew(user);
        long today = 0;
        int files = 0;
        Deque<Entry> entries = ledger.get(user.getId());
        if (entries != null) {
            synchronized (entries) {
                prune(entries);
                today = entries.stream().mapToLong(Entry::bytes).sum();
                files = entries.stream().mapToInt(Entry::files).sum();
            }
        }
        if (limits == null) {
            return new Usage(used, Long.MAX_VALUE, today, Long.MAX_VALUE, files, Integer.MAX_VALUE, fresh);
        }
        return new Usage(used, limits.storageQuotaBytes(), today,
                fresh ? limits.newAccountDailyUploadBytes() : limits.dailyUploadBytes(), files,
                fresh ? limits.newAccountDailyUploadCount() : limits.dailyUploadCount(), fresh);
    }

    /** Everything the account stores right now, in bytes. */
    @Transactional(readOnly = true)
    public long storedBytes(UUID userId, User user) {
        long posts = sum("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM PostMedia m WHERE m.post.author.id = :u", userId);
        long stories = sum("SELECT COALESCE(SUM(s.sizeBytes), 0) FROM Story s WHERE s.author.id = :u", userId);
        long messages = sum("SELECT COALESCE(SUM(m.attachmentBytes), 0) FROM Message m "
                + "WHERE m.sender.id = :u AND m.deletedAt IS NULL", userId);
        long profile = user == null ? 0 : user.getAvatarBytes() + user.getBannerBytes();
        return posts + stories + messages + profile;
    }

    /** Forgets the daily ledger. Used by tests. */
    public void reset() {
        ledger.clear();
    }

    private long sum(String jpql, UUID userId) {
        Number n = em.createQuery(jpql, Number.class).setParameter("u", userId).getSingleResult();
        return n == null ? 0 : n.longValue();
    }

    private boolean isNew(User user) {
        Duration period = limits == null || limits.newAccountPeriod() == null ? Duration.ZERO : limits.newAccountPeriod();
        return user.getCreatedAt() != null && user.getCreatedAt().isAfter(Instant.now().minus(period));
    }

    private static void prune(Deque<Entry> entries) {
        long cutoff = System.currentTimeMillis() - DAY.toMillis();
        while (!entries.isEmpty() && entries.peekFirst().at() < cutoff) {
            entries.pollFirst();
        }
    }

    static String human(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            double gb = bytes / (1024.0 * 1024 * 1024);
            return (gb == Math.floor(gb) ? String.valueOf((long) gb) : String.format("%.1f", gb)) + " GB";
        }
        return (bytes / (1024 * 1024)) + " MB";
    }
}
