package com.mk3.chatapp.vip;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/vip")
@RequiredArgsConstructor
@PreAuthorize("@security.isSuperAdmin()")
public class VipStatisticsController {
    private final VipStatisticsService statistics;

    @GetMapping("/statistics")
    public VipStatisticsResponse statistics(@RequestParam(defaultValue = "90") int days) {
        return statistics.statistics(days);
    }
}
