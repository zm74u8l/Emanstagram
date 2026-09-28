package com.emanstagram.post;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.post.dto.PostDtos.*;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService service;
    private final CurrentUser currentUser;

    public PostController(PostService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /**
     * Multipart upload: {@code files} (1-10), an optional {@code meta} JSON
     * array with per-file dimensions and blurhash, plus caption, location and
     * visibility.
     */
    @PostMapping(path = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "meta", required = false) String meta,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "visibility", required = false) PostVisibility visibility) {
        PostResponse created = service.create(currentUser.require(), files, meta, caption, location, visibility);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/posts/{id}")
    public PostResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.require());
    }

    @PatchMapping("/posts/{id}")
    public PostResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePostRequest request) {
        return service.update(id, currentUser.require(), request);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{id}/like")
    public LikeState like(@PathVariable UUID id) {
        return service.like(id, currentUser.require());
    }

    @DeleteMapping("/posts/{id}/like")
    public LikeState unlike(@PathVariable UUID id) {
        return service.unlike(id, currentUser.require());
    }

    @GetMapping("/posts/{id}/likes")
    public CursorPage<UserSummary> likers(@PathVariable UUID id,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer limit) {
        return service.likers(id, currentUser.require(), cursor, limit);
    }

    @PutMapping("/posts/{id}/save")
    public SaveState save(@PathVariable UUID id) {
        return service.save(id, currentUser.require());
    }

    @DeleteMapping("/posts/{id}/save")
    public SaveState unsave(@PathVariable UUID id) {
        return service.unsave(id, currentUser.require());
    }

    @GetMapping("/users/{username}/posts")
    public CursorPage<PostResponse> byUser(@PathVariable String username,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        return service.byUser(username, currentUser.require(), cursor, limit);
    }

    @GetMapping("/me/saved")
    public CursorPage<PostResponse> saved(@RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer limit) {
        return service.saved(currentUser.require(), cursor, limit);
    }
}
