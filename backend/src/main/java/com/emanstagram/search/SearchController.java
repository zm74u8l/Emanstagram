package com.emanstagram.search;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.SqlLike;
import com.emanstagram.common.TextTokens;
import com.emanstagram.post.PostRepository;
import com.emanstagram.social.AccessPolicy;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Search across people and hashtags.
 *
 * <p>Hashtags are not stored in their own table; they are read out of recent
 * public captions. That is plenty at this scale, and a {@code post_tags} table
 * can replace it later without changing the response shape.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int CAPTION_SAMPLE = 300;

    public record TagResult(String tag, long postCount) {
    }

    public record SearchResponse(List<UserSummary> users, List<TagResult> tags) {
    }

    private final UserRepository users;
    private final PostRepository posts;
    private final UserViews userViews;
    private final AccessPolicy access;
    private final CurrentUser currentUser;

    public SearchController(UserRepository users, PostRepository posts, UserViews userViews,
                            AccessPolicy access, CurrentUser currentUser) {
        this.users = users;
        this.posts = posts;
        this.userViews = userViews;
        this.access = access;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public SearchResponse search(@RequestParam("q") String rawQuery) {
        User me = currentUser.require();
        String q = rawQuery.trim();
        if (q.isEmpty()) {
            return new SearchResponse(List.of(), List.of());
        }

        List<TagResult> tags = List.of();
        List<UserSummary> people = List.of();
        Set<UUID> hidden = access.hiddenFrom(me.getId());

        if (q.startsWith("#")) {
            tags = tags(q.substring(1), hidden);
        } else {
            String term = q.startsWith("@") ? q.substring(1) : q;
            if (!term.isEmpty()) {
                List<User> found = users.search(SqlLike.escape(term), me.getId(), PageRequest.of(0, 20))
                        .getContent().stream()
                        .filter(u -> !hidden.contains(u.getId())).toList();
                people = userViews.summariesWithFollowing(found, me.getId());
                tags = tags(term, hidden);
            }
        }
        return new SearchResponse(people, tags);
    }

    private List<TagResult> tags(String prefix, Set<UUID> hidden) {
        // Tags only ever contain letters, digits and underscores (the same
        // rule as TextTokens). The underscore is kept, so #my_tag is findable,
        // and escaped, so it isn't read as LIKE's any-character wildcard.
        String p = prefix.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_]", "");
        if (p.isEmpty()) {
            return List.of();
        }
        List<String> captions = posts.captionsMatching("%#" + SqlLike.escape(p) + "%", hidden,
                PageRequest.of(0, CAPTION_SAMPLE));
        Map<String, Long> counts = captions.stream()
                .flatMap(c -> TextTokens.hashtags(c).stream())
                .filter(t -> t.startsWith(p))
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8)
                .map(e -> new TagResult(e.getKey(), e.getValue()))
                .toList();
    }
}
