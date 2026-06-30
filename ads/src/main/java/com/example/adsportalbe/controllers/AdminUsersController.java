package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.AdminUserDto;
import com.example.adsportalbe.dto.requests.UserSearchRequestDto;
import com.example.adsportalbe.services.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ads-portal/admin/users")
@RequiredArgsConstructor
public class AdminUsersController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<Page<AdminUserDto>> getUsers(@ModelAttribute UserSearchRequestDto request) {
        Page<AdminUserDto> result = adminUserService.searchUsers(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDto> getUserById(@org.springframework.web.bind.annotation.PathVariable Long id) {
        AdminUserDto result = adminUserService.getUserById(id);
        return ResponseEntity.ok(result);
    }
}
