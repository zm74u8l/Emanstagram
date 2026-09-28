package com.emanstagram.moderation;

import com.emanstagram.comment.Comment;
import com.emanstagram.comment.CommentRepository;
import com.emanstagram.comment.CommentService;
import com.emanstagram.common.ApiException;
import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.post.Post;
import com.emanstagram.post.PostMedia;
import com.emanstagram.post.PostMediaRepository;
import com.emanstagram.post.PostRepository;
import com.emanstagram.post.PostService;
import com.emanstagram.post.PostVisibility;
import com.emanstagram.storage.SupabaseStorageService;
import com.emanstagram.user.User;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** User reports and the moderator review queue. */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    public enum Action {
        /** Nothing wrong: close the report. */
        DISMISS,
        /** Delete the reported post or comment, which also closes its reports. */
        REMOVE_CONTENT
    }

    public record ReportRequest(
            UUID userId,
            UUID postId,
            UUID commentId,
            @NotNull(message = "Choose a reason")
            ReportReason reason,
            @Size(max = 1000, message = "Details are limited to 1000 characters")
            String details
    ) {
    }

    public record ReportReceipt(UUID id) {
    }

    public record ResolveRequest(@NotNull Action action) {
    }

    public record PostPreview(UUID id, String caption, String thumbUrl, UserSummary author,
                              PostVisibility visibility) {
    }

    public record CommentPreview(UUID id, UUID postId, String body, UserSummary author) {
    }

    public record ReportResponse(
            UUID id,
            ReportReason reason,
            String details,
            boolean resolved,
            Instant createdAt,
            UserSummary reporter,
            String targetType,
            UserSummary targetUser,
            PostPreview post,
            CommentPreview comment
    ) {
    }

    public record QueueStats(long open) {
    }

    private final ReportRepository reports;
    private final PostRepository posts;
    private final PostMediaRepository media;
    private final CommentRepository comments;
    private final PostService postService;
    private final CommentService commentService;
    private final UserViews userViews;
    private final SupabaseStorageService storage;

    public ModerationService(ReportRepository reports, PostRepository posts, PostMediaRepository media,
                             CommentRepository comments, PostService postService,
                             CommentService commentService, UserViews userViews,
                             SupabaseStorageService storage) {
        this.reports = reports;
        this.posts = posts;
        this.media = media;
        this.comments = comments;
        this.postService = postService;
        this.commentService = commentService;
        this.userViews = userViews;
        this.storage = storage;
    }

    /**
     * Files a report. Reporting the same thing twice while the first report is
     * still open returns the existing one, so a frustrated user can't flood
     * the queue.
     */
    @Transactional
    public ReportReceipt report(User reporter, ReportRequest request) {
        UUID userId = request.userId();
        UUID postId = request.postId();
        UUID commentId = request.commentId();
        if (userId == null && postId == null && commentId == null) {
            throw ApiException.badRequest("NO_TARGET", "Say what you're reporting.");
        }
        if (reporter.getId().equals(userId)) {
            throw ApiException.badRequest("SELF_ACTION", "You can't report yourself.");
        }
        if (postId != null && !posts.existsById(postId)) {
            throw ApiException.notFound("POST_NOT_FOUND", "That post no longer exists.");
        }
        if (commentId != null && !comments.existsById(commentId)) {
            throw ApiException.notFound("COMMENT_NOT_FOUND", "That comment no longer exists.");
        }
        List<Report> existing = reports.openDuplicate(reporter.getId(), userId, postId, commentId);
        if (!existing.isEmpty()) {
            return new ReportReceipt(existing.get(0).getId());
        }
        String details = request.details() == null || request.details().isBlank() ? null : request.details().trim();
        Report saved = reports.save(new Report(reporter.getId(), userId, postId, commentId, request.reason(), details));
        log.info("Report {} filed: {} (user={}, post={}, comment={})", saved.getId(), request.reason(),
                userId, postId, commentId);
        return new ReportReceipt(saved.getId());
    }

    @Transactional(readOnly = true)
    public CursorPage<ReportResponse> queue(boolean resolved, String cursor, Integer limit) {
        int size = CursorPage.clamp(limit == null ? 20 : limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Report> rows = reports.queuePage(resolved, c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, r -> new Cursor(r.getCreatedAt(), r.getId()), this::assemble);
    }

    @Transactional(readOnly = true)
    public QueueStats stats() {
        return new QueueStats(reports.countByResolvedFalse());
    }

    @Transactional
    public void resolve(UUID reportId, Action action, User moderator) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "That report was already handled."));

        if (action == Action.DISMISS) {
            report.setResolved(true);
        } else if (report.getCommentId() != null) {
            // Deleting the content also deletes every report about it.
            Comment comment = comments.findById(report.getCommentId()).orElse(null);
            if (comment != null) {
                commentService.deleteComment(comment);
            }
        } else if (report.getPostId() != null) {
            Post post = posts.findWithAuthorById(report.getPostId()).orElse(null);
            if (post != null) {
                postService.deletePost(post);
            }
        } else {
            throw ApiException.badRequest("NOTHING_TO_REMOVE",
                    "Account reports can only be dismissed. Remove the offending posts individually.");
        }
        log.info("Report {} resolved by {} with {}", reportId, moderator.getUsername(), action);
    }

    private List<ReportResponse> assemble(List<Report> rows) {
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> postIds = new HashSet<>();
        Set<UUID> commentIds = new HashSet<>();
        for (Report r : rows) {
            userIds.add(r.getReporterId());
            if (r.getTargetUserId() != null) {
                userIds.add(r.getTargetUserId());
            }
            if (r.getPostId() != null) {
                postIds.add(r.getPostId());
            }
            if (r.getCommentId() != null) {
                commentIds.add(r.getCommentId());
            }
        }

        Map<UUID, Comment> commentsById = new HashMap<>();
        comments.findAllById(commentIds).forEach(c -> {
            commentsById.put(c.getId(), c);
            userIds.add(c.getAuthorId());
        });
        Map<UUID, Post> postsById = new HashMap<>();
        if (!postIds.isEmpty()) {
            posts.findAllWithAuthorByIdIn(postIds).forEach(p -> postsById.put(p.getId(), p));
        }
        Map<UUID, String> thumbs = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (PostMedia m : media.findForPosts(postIds)) {
                thumbs.putIfAbsent(m.getPost().getId(), storage.publicUrl(m.getStorageKey()));
            }
        }
        Map<UUID, User> usersById = userViews.load(userIds);

        List<ReportResponse> result = new ArrayList<>();
        for (Report r : rows) {
            Post p = r.getPostId() == null ? null : postsById.get(r.getPostId());
            Comment c = r.getCommentId() == null ? null : commentsById.get(r.getCommentId());
            String type = c != null ? "COMMENT" : p != null ? "POST" : "USER";
            result.add(new ReportResponse(
                    r.getId(), r.getReason(), r.getDetails(), r.isResolved(), r.getCreatedAt(),
                    userViews.summary(usersById.get(r.getReporterId())),
                    type,
                    r.getTargetUserId() == null ? null : userViews.summary(usersById.get(r.getTargetUserId())),
                    p == null ? null : new PostPreview(p.getId(), p.getCaption(), thumbs.get(p.getId()),
                            userViews.summary(p.getAuthor()), p.getVisibility()),
                    c == null ? null : new CommentPreview(c.getId(), c.getPostId(), c.getBody(),
                            userViews.summary(usersById.get(c.getAuthorId())))));
        }
        return result;
    }
}
