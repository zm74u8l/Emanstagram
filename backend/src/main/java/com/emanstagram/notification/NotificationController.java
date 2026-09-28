package com.emanstagram.notification;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.notification.NotificationService.NotificationResponse;
import com.emanstagram.notification.NotificationService.UnreadCount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public CursorPage<NotificationResponse> list(@RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer limit) {
        return service.page(currentUser.require().getId(), cursor, limit);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return service.unreadCount(currentUser.require().getId());
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        service.markAllRead(currentUser.require().getId());
        return ResponseEntity.noContent().build();
    }
}
