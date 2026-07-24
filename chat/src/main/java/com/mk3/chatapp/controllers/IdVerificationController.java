package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.responses.IdVerificationSessionResponseDTO;
import com.mk3.chatapp.dtos.responses.IdVerificationStatusResponseDTO;
import com.mk3.chatapp.services.IdVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/id-verification")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class IdVerificationController {

    private final IdVerificationService idVerificationService;

    @PostMapping("/session")
    public ResponseEntity<IdVerificationSessionResponseDTO> createSession() {
        return ResponseEntity.ok(idVerificationService.createVerificationSession());
    }

    @GetMapping("/status")
    public ResponseEntity<IdVerificationStatusResponseDTO> getStatus() {
        return ResponseEntity.ok(idVerificationService.getOwnStatus());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {
        idVerificationService.handleWebhookEvent(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
