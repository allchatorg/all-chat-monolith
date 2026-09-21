package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.responses.ProBadgeDTO;
import com.mk3.chatapp.services.ProBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pro")
public class ProBadgeController {
    private final ProBadgeService badges;

    @PostMapping("/badges")
    public List<ProBadgeDTO> lookup(@RequestBody List<Long> userIds) {
        return badges.lookup(userIds);
    }
}
