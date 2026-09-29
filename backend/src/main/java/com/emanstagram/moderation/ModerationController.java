package com.emanstagram.moderation;

import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.moderation.ModerationService.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ModerationController {

    private final ModerationService moderation;
    private final AccountModerationService accounts;
    private final CurrentUser currentUser;

    public ModerationController(ModerationService moderation, AccountModerationService accounts,
                                CurrentUser currentUser) {
        this.moderation = moderation;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    /** Accounts sorted by storage used, newest, or suspended; optionally filtered by name or email. */
    @GetMapping("/admin/accounts")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public List<AccountModerationService.AccountView> accounts(
            @RequestParam(defaultValue = "STORAGE") AccountModerationService.Sort sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        return accounts.list(sort, q, limit);
    }

    @PostMapping("/admin/accounts/{id}/suspend")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public AccountModerationService.SuspendResult suspend(
            @PathVariable UUID id, @Valid @RequestBody AccountModerationService.SuspendRequest request) {
        return accounts.suspend(id, request, currentUser.require());
    }

    @DeleteMapping("/admin/accounts/{id}/suspend")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> unsuspend(@PathVariable UUID id) {
        accounts.unsuspend(id, currentUser.require());
        return ResponseEntity.noContent().build();
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
