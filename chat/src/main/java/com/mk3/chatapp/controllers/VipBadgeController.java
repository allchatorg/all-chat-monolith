package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.responses.VipBadgeDTO;
import com.mk3.chatapp.services.VipBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vip")
public class VipBadgeController {
    private final VipBadgeService badges;

    @PostMapping("/badges")
    public List<VipBadgeDTO> lookup(@RequestBody List<Long> userIds) {
        return badges.lookup(userIds);
    }
}
