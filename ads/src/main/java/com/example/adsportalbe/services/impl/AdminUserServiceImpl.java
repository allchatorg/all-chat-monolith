package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.AdminUserDto;
import com.example.adsportalbe.dto.requests.SortDto;
import com.example.adsportalbe.dto.requests.UserSearchRequestDto;
import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.repositories.AdsUserRepository;
import com.example.adsportalbe.services.AdminUserService;
import com.example.adsportalbe.specifications.UserSpecification;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdsUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminUserDto getUserById(Long id) {
        return userRepository.findAdminUserById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserDto> searchUsers(UserSearchRequestDto request) {
        List<Sort.Order> orders = jsonStringToSortOrder(request.sort());
        Sort sort = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);

        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 10;
        Pageable pageable = PageRequest.of(page, size, sort);
        Specification<User> spec = UserSpecification.getSpecification(request);

        return userRepository.findAll(spec, pageable)
                .map(user -> AdminUserDto.builder()
                        .id(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .totalPurchasedAdsCount(user.getPurchasedAdsCount())
                        .totalSpent(user.getTotalSpent())
                        .createdAt(user.getCreatedAt())
                        .build());
    }

    private List<SortDto> jsonStringToSortDto(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String decoded = URLDecoder.decode(jsonString, StandardCharsets.UTF_8);
            ObjectMapper obj = new ObjectMapper();
            return obj.readValue(decoded, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Sort.Order> jsonStringToSortOrder(String jsonString) {
        return jsonStringToSortDto(jsonString)
                .stream()
                .map(sortDto -> new Sort.Order(
                        Sort.Direction.fromString(sortDto.direction()),
                        sortDto.field()))
                .collect(Collectors.toList());
    }
}
