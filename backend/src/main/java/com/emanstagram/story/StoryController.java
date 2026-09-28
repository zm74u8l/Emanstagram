package com.emanstagram.story;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.story.StoryService.StoryGroup;
import com.emanstagram.story.StoryService.StoryItem;
import com.emanstagram.story.StoryService.StoryViewer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService stories;
    private final CurrentUser currentUser;

    public StoryController(StoryService stories, CurrentUser currentUser) {
        this.stories = stories;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryItem> create(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "caption", required = false) String caption,
                                            @RequestParam(value = "backgroundHex", required = false) String backgroundHex) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stories.create(currentUser.require(), file, caption, backgroundHex));
    }

    @GetMapping("/feed")
    public List<StoryGroup> tray() {
        return stories.tray(currentUser.require());
    }

    @GetMapping("/user/{username}")
    public StoryGroup forUser(@PathVariable String username) {
        return stories.forUser(username, currentUser.require());
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> view(@PathVariable UUID id) {
        stories.markViewed(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/viewers")
    public List<StoryViewer> viewers(@PathVariable UUID id) {
        return stories.viewers(id, currentUser.require());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        stories.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
