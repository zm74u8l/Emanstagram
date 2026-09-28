package com.emanstagram.moderation;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.moderation.ModerationService.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ModerationController {

    private final ModerationService moderation;
    private final CurrentUser currentUser;

    public ModerationController(ModerationService moderation, CurrentUser currentUser) {
        this.moderation = moderation;
        this.currentUser = currentUser;
    }

    /** Any signed-in user can report an account, post or comment. */
    @PostMapping("/reports")
    public ResponseEntity<ReportReceipt> report(@Valid @RequestBody ReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moderation.report(currentUser.require(), request));
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public CursorPage<ReportResponse> queue(@RequestParam(defaultValue = "open") String status,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        return moderation.queue("resolved".equalsIgnoreCase(status), cursor, limit);
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public QueueStats stats() {
        return moderation.stats();
    }

    @PostMapping("/admin/reports/{id}/resolve")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
        moderation.resolve(id, request.action(), currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
