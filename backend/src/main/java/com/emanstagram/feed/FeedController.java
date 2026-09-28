package com.emanstagram.feed;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.post.dto.PostDtos.MosaicTile;
import com.emanstagram.post.dto.PostDtos.PostResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FeedController {

    private final FeedService feed;
    private final CurrentUser currentUser;

    public FeedController(FeedService feed, CurrentUser currentUser) {
        this.feed = feed;
        this.currentUser = currentUser;
    }

    @GetMapping("/feed")
    public CursorPage<PostResponse> home(@RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit) {
        return feed.home(currentUser.require(), cursor, limit);
    }

    @GetMapping("/explore")
    public CursorPage<PostResponse> explore(@RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        return feed.explore(currentUser.require(), cursor, limit);
    }

    @GetMapping("/tags/{tag}/posts")
    public CursorPage<PostResponse> tagged(@PathVariable String tag,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        return feed.tagged(tag, currentUser.require(), cursor, limit);
    }

    /** Public: the signed-out login screen's photo mosaic. Cached briefly to spare the database. */
    @GetMapping("/public/mosaic")
    public ResponseEntity<List<MosaicTile>> mosaic() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(feed.mosaic());
    }
}
