package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.requests.BanAppealRequestDTO;
import com.mk3.chatapp.dtos.responses.BanAppealUserViewDTO;
import com.mk3.chatapp.dtos.responses.MyBanContextDTO;
import com.mk3.chatapp.services.BanAppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Banned-user endpoints. Deliberately id-free: every lookup is keyed off the
 * authenticated user, so one banned user can never read another's ban or appeal.
 * Whitelisted for banned users in AccessRestrictionFilter.
 */
@RestController
@RequestMapping("/api/v1/ban-appeals")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class BanAppealController {

    private final BanAppealService banAppealService;

    @GetMapping("/my-ban")
    public ResponseEntity<MyBanContextDTO> getMyBan() {
        return ResponseEntity.ok(banAppealService.getMyBanContext());
    }

    @PostMapping
    public ResponseEntity<BanAppealUserViewDTO> submitAppeal(@Valid @RequestBody BanAppealRequestDTO request) {
        return ResponseEntity.ok(banAppealService.submitAppeal(request));
    }

    @GetMapping("/my-appeal")
    public ResponseEntity<BanAppealUserViewDTO> getMyAppeal() {
        return ResponseEntity.ok(banAppealService.getMyAppeal());
    }
}
