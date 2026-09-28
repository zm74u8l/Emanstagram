package com.emanstagram.feed;

import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.common.TextTokens;
import com.emanstagram.post.Post;
import com.emanstagram.post.PostAssembler;
import com.emanstagram.post.PostMedia;
import com.emanstagram.post.PostMediaRepository;
import com.emanstagram.post.PostRepository;
import com.emanstagram.post.PostService;
import com.emanstagram.post.dto.PostDtos.MosaicTile;
import com.emanstagram.post.dto.PostDtos.PostResponse;
import com.emanstagram.social.AccessPolicy;
import com.emanstagram.storage.StorageService;
import com.emanstagram.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Home feed, explore, hashtag pages and the signed-out mosaic. */
@Service
public class FeedService {

    private static final int MOSAIC_SIZE = 24;

    private final PostRepository posts;
    private final PostMediaRepository media;
    private final PostAssembler assembler;
    private final AccessPolicy access;
    private final StorageService storage;

    public FeedService(PostRepository posts, PostMediaRepository media, PostAssembler assembler,
                       AccessPolicy access, StorageService storage) {
        this.posts = posts;
        this.media = media;
        this.assembler = assembler;
        this.access = access;
        this.storage = storage;
    }

    /** People you follow, plus yourself, newest first. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> home(User viewer, String cursor, Integer limit) {
        int size = CursorPage.clamp(limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Post> rows = posts.feed(viewer.getId(), c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, PostService::cursorOf, page -> assembler.assemble(page, viewer.getId()));
    }

    /**
     * Every public post except your own. People you follow are deliberately
     * included: on a young network, leaving them out would empty the page.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> explore(User viewer, String cursor, Integer limit) {
        int size = CursorPage.clamp(limit == null ? 24 : limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Post> rows = posts.explore(viewer.getId(), access.hiddenFrom(viewer.getId()),
                c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, PostService::cursorOf, page -> assembler.assemble(page, viewer.getId()));
    }

    /**
     * Posts carrying an exact hashtag. The query's LIKE also matches longer
     * tags (#cat finds #caterpillar), so those are filtered out here. A page
     * can therefore come back a little short, which infinite scroll absorbs.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> tagged(String rawTag, User viewer, String cursor, Integer limit) {
        String tag = rawTag.replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        int size = CursorPage.clamp(limit == null ? 24 : limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Post> rows = posts.taggedCandidates("%#" + escapeLike(tag) + "%", access.hiddenFrom(viewer.getId()),
                c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, PostService::cursorOf,
                page -> assembler.assemble(page.stream().filter(p -> TextTokens.hasTag(p.getCaption(), tag)).toList(),
                        viewer.getId()));
    }

    /** Cover images for the login screen. Public posts only, and nothing that identifies who posted. */
    @Transactional(readOnly = true)
    public List<MosaicTile> mosaic() {
        if (!storage.isConfigured()) {
            return List.of();
        }
        List<PostMedia> covers = media.publicCovers(PageRequest.of(0, MOSAIC_SIZE));
        return covers.stream()
                .map(m -> new MosaicTile(storage.publicUrl(m.getStorageKey()), m.getBlurhash(),
                        m.getWidth(), m.getHeight()))
                .toList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
