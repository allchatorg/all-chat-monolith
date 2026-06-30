package com.example.adsportalbe.utils;

import com.example.adsportalbe.dto.requests.SortDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
public final class Utils {

    public static List<SortDto> jsonStringToSortDto(String jsonString) {
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

    public static List<Sort.Order> jsonStringToSortOrder(String jsonString) {
        return jsonStringToSortDto(jsonString)
                .stream()
                .map(sortDto -> new Sort.Order(
                        Sort.Direction.fromString(sortDto.direction()),
                        sortDto.field()))
                .collect(Collectors.toList());
    }
}
