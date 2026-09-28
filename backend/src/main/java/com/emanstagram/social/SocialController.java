package com.emanstagram.social;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.social.SocialService.BlockState;
import com.emanstagram.social.SocialService.FollowState;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SocialController {

    private final SocialService service;
    private final CurrentUser currentUser;

    public SocialController(SocialService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PutMapping("/users/{id}/follow")
    public FollowState follow(@PathVariable UUID id) {
        return service.follow(id, currentUser.require());
    }

    @DeleteMapping("/users/{id}/follow")
    public FollowState unfollow(@PathVariable UUID id) {
        return service.unfollow(id, currentUser.require());
    }

    @DeleteMapping("/me/followers/{id}")
    public ResponseEntity<Void> removeFollower(@PathVariable UUID id) {
        service.removeFollower(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/block")
    public BlockState block(@PathVariable UUID id) {
        return service.block(id, currentUser.require());
    }

    @DeleteMapping("/users/{id}/block")
    public BlockState unblock(@PathVariable UUID id) {
        return service.unblock(id, currentUser.require());
    }

    @GetMapping("/me/blocked")
    public List<UserSummary> blocked() {
        return service.blocked(currentUser.require());
    }

    @GetMapping("/users/{username}/followers")
    public CursorPage<UserSummary> followers(@PathVariable String username,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer limit) {
        return service.followers(username, currentUser.require(), cursor, limit);
    }

    @GetMapping("/users/{username}/following")
    public CursorPage<UserSummary> following(@PathVariable String username,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer limit) {
        return service.following(username, currentUser.require(), cursor, limit);
    }

    @GetMapping("/users/suggested")
    public List<UserSummary> suggested(@RequestParam(required = false) Integer limit) {
        return service.suggested(currentUser.require(), limit);
    }
}
