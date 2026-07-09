package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.requests.BanAppealResolutionRequestDTO;
import com.mk3.chatapp.dtos.responses.BanAppealAdminDetailDTO;
import com.mk3.chatapp.dtos.responses.BanAppealAdminListDTO;
import com.mk3.chatapp.enums.BanAppealStatus;
import com.mk3.chatapp.services.BanAppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin review of ban appeals. Authorization (ADMIN or SUPER_ADMIN) is enforced
 * at the service layer via @PreAuthorize("@security.isAdmin()").
 */
@RestController
@RequestMapping("/api/v1/admin/ban-appeals")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class BanAppealAdminController {

    private final BanAppealService banAppealService;

    @GetMapping
    public ResponseEntity<Page<BanAppealAdminListDTO>> listAppeals(
            @RequestParam(required = false) BanAppealStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(banAppealService.listAppeals(status, openOnly, page, pageSize));
    }

    @GetMapping("/{appealId}")
    public ResponseEntity<BanAppealAdminDetailDTO> getAppeal(@PathVariable Long appealId) {
        return ResponseEntity.ok(banAppealService.getAppeal(appealId));
    }

    @PostMapping("/{appealId}/claim")
    public ResponseEntity<BanAppealAdminDetailDTO> claimAppeal(@PathVariable Long appealId) {
        return ResponseEntity.ok(banAppealService.claimAppeal(appealId));
    }

    @PostMapping("/{appealId}/resolve")
    public ResponseEntity<BanAppealAdminDetailDTO> resolveAppeal(
            @PathVariable Long appealId,
            @Valid @RequestBody BanAppealResolutionRequestDTO request) {
        return ResponseEntity.ok(banAppealService.resolveAppeal(appealId, request));
    }
}
