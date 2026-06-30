package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.AdminUserDto;
import com.example.adsportalbe.dto.requests.UserSearchRequestDto;
import org.springframework.data.domain.Page;

public interface AdminUserService {

    AdminUserDto getUserById(Long id);

    Page<AdminUserDto> searchUsers(UserSearchRequestDto request);
}
