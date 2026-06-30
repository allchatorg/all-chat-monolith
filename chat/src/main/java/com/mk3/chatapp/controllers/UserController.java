package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(Principal connectedUser) {
        var user = userService.getUserInfo(connectedUser);

        return ResponseEntity.ok(user);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserMinimalDTO>> searchUsersForChat(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal connectedUser) {
        return ResponseEntity.ok(userService.searchClaimedUsersForChat(query, connectedUser, page, size));
    }

    @PatchMapping("/change-username")
    public ResponseEntity<Void> changeUsername(@RequestParam String username, Principal connectedUser) {
        userService.changeUsername(username, connectedUser);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/request-email-update")
    public ResponseEntity<Map<String, String>> requestEmailUpdate(@Valid @RequestBody RequestEmailUpdateDTO request,
                                                                  Principal connectedUser) {
        userService.requestEmailUpdate(request, connectedUser);
        return ResponseEntity.ok(Map.of("message", "Verification code sent to new email"));
    }

    @PatchMapping("/verify-email-update")
    public ResponseEntity<UserDTO> verifyEmailUpdate(@Valid @RequestBody VerifyEmailUpdateDTO request,
                                                     Principal connectedUser) {
        return ResponseEntity.ok(userService.verifyEmailUpdate(request, connectedUser));
    }

    @PatchMapping("/marketing-preferences")
    public ResponseEntity<Void> updateMarketingPreferences(@Valid @RequestBody UpdateMarketingPreferencesDTO request,
                                                           Principal connectedUser) {
        userService.updateMarketingPreferences(request, connectedUser);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/update-age")
    public ResponseEntity<Boolean> updateAge(@RequestParam boolean isOver18, Principal connectedUser) {
        userService.updateAge(isOver18, connectedUser);
        return ResponseEntity.ok(isOver18);
    }

    @PatchMapping("/update-blurred-content")
    public ResponseEntity<List<TagDTO>> updateBlurredContent(
            @RequestBody List<Long> tagIds,
            Principal connectedUser) {

        List<TagDTO> blurredContent = userService.updateBlurredContent(tagIds, connectedUser);
        return ResponseEntity.ok(blurredContent);
    }

    @PatchMapping("/update-display-color")
    public ResponseEntity<String> updateUserDisplayColor(@RequestParam String color) {
        return ResponseEntity.ok(userService.updateUserDisplayColor(color));
    }

    @PatchMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequestDTO changePasswordRequestDTO,
                                               Principal connectedUser) {
        userService.changePassword(changePasswordRequestDTO, connectedUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send-email-verification")
    public ResponseEntity<Void> sendEmailVerification(Principal connectedUser) {
        userService.sendEmailVerification(connectedUser);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/verify")
    public ResponseEntity<UserDTO> verify(@RequestParam String token) {
        return ResponseEntity.ok().body(userService.verifyEmail(token));
    }

    @PostMapping("/send-phone-verification")
    public ResponseEntity<Void> sendPhoneVerification(@RequestBody AddPhoneNumberRequest request) {
        userService.sendPhoneVerification(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/verify-phone")
    public ResponseEntity<UserDTO> verifyPhone(@RequestParam String token) {
        return ResponseEntity.ok().body(userService.verifyPhone(token));
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<Void> deleteAccount(@RequestBody DeleteAccountRequest deleteAccountRequest,
                                              Principal connectedUser) {
        User user = userService.getPrincipal(connectedUser);
        userService.deleteAccount(user, deleteAccountRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/block/{userId}")
    public ResponseEntity<Void> blockUser(@PathVariable Long userId, Principal connectedUser) {
        userService.blockUser(userId, connectedUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unblock/{userId}")
    public ResponseEntity<Void> unblockUser(@PathVariable Long userId, Principal connectedUser) {
        userService.unblockUser(userId, connectedUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/blocked")
    public ResponseEntity<List<UserMinimalDTO>> getBlockedUsers(Principal connectedUser) {
        return ResponseEntity.ok(userService.getBlockedUsers(connectedUser));
    }

    @PostMapping("/moderator-application")
    public ResponseEntity<Void> submitModeratorApplication(
            @Valid @RequestBody ModeratorApplicationRequest request,
            Principal connectedUser) {

        userService.submitModeratorApplication(connectedUser, request);

        return ResponseEntity.ok().build();
    }

}
