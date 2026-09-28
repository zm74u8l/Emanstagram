package com.emanstagram.comment;

import com.emanstagram.comment.CommentService.CommentLikeState;
import com.emanstagram.comment.CommentService.CommentResponse;
import com.emanstagram.comment.CommentService.CreateCommentRequest;
import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService service;
    private final CurrentUser currentUser;

    public CommentController(CommentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/posts/{postId}/comments")
    public CursorPage<CommentResponse> list(@PathVariable UUID postId,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        return service.topLevel(postId, currentUser.require(), cursor, limit);
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(@PathVariable UUID postId,
                                                  @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(postId, currentUser.require(), request));
    }

    @GetMapping("/comments/{id}/replies")
    public CursorPage<CommentResponse> replies(@PathVariable UUID id,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        return service.replies(id, currentUser.require(), cursor, limit);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/comments/{id}/like")
    public CommentLikeState like(@PathVariable UUID id) {
        return service.like(id, currentUser.require());
    }

    @DeleteMapping("/comments/{id}/like")
    public CommentLikeState unlike(@PathVariable UUID id) {
        return service.unlike(id, currentUser.require());
    }
}
