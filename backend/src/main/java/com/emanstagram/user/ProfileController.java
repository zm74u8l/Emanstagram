package com.emanstagram.user;

import com.emanstagram.abuse.UploadGuard;
import com.emanstagram.auth.AuthService;
import com.emanstagram.auth.dto.AuthDtos.TokenResponse;
import com.emanstagram.auth.dto.AuthDtos.UserResponse;
import com.emanstagram.common.CurrentUser;
import com.emanstagram.user.dto.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profiles;
    private final AuthService auth;
    private final CurrentUser currentUser;
    private final UploadGuard uploadGuard;

    public ProfileController(ProfileService profiles, AuthService auth, CurrentUser currentUser,
                             UploadGuard uploadGuard) {
        this.profiles = profiles;
        this.auth = auth;
        this.currentUser = currentUser;
        this.uploadGuard = uploadGuard;
    }

    /** How much of the storage quota and today's upload budget the caller has used. */
    @GetMapping("/me/storage")
    public UploadGuard.Usage storage() {
        return uploadGuard.usage(currentUser.require());
    }

    @GetMapping("/users/username/{username}")
    public ProfileResponse profile(@PathVariable String username) {
        return profiles.profile(username, currentUser.require());
    }

    @PatchMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(currentUser.require(), request);
    }

    @PutMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse avatar(@RequestParam("file") MultipartFile file) {
        return profiles.setAvatar(currentUser.require(), file);
    }

    @DeleteMapping("/me/avatar")
    public UserResponse clearAvatar() {
        return profiles.clearAvatar(currentUser.require());
    }

    @PutMapping(path = "/me/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse banner(@RequestParam("file") MultipartFile file) {
        return profiles.setBanner(currentUser.require(), file);
    }

    @DeleteMapping("/me/banner")
    public UserResponse clearBanner() {
        return profiles.clearBanner(currentUser.require());
    }

    /**
     * Changes the password, signs out every other device, and returns a fresh
     * token pair so this device stays signed in.
     */
    @PostMapping("/me/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return auth.changePassword(currentUser.require().getId(), request);
    }

    /** Public, for the sign-up form. Usernames are public anyway, so this leaks nothing new. */
    @GetMapping("/auth/username-available")
    public UsernameAvailability available(@RequestParam String username) {
        UUID me = currentUser.get().map(User::getId).orElse(null);
        return profiles.availability(username, me);
    }
}
