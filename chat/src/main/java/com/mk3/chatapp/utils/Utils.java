package com.mk3.chatapp.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.dtos.requests.SortDto;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor
public final class Utils {
    public static List<SortDto> jsonStringToSortDto(String jsonString) {
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
                        sortDto.field()
                ))
                .collect(Collectors.toList());
    }

    public static boolean isValidHexColor(String color) {
        return color != null && color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }

    public static String generateRandomHexColor() {
        StringBuilder color = new StringBuilder("#");
        for (int i = 0; i < 6; i++) {
            int randomHex = (int) (Math.random() * 16);
            color.append(Integer.toHexString(randomHex));
        }
        return color.toString();
    }

    public static boolean isReservedUsername(String normalizedName) {
        Set<String> reservedNames = Set.of(
                "allchat", "allchatorg", "altchat", "altchatorg",
                "admin", "administrator", "mod", "moderator",
                "server", "ad", "sponsor", "sponsored",
                "advertisement", "targeted ad", "targeted advertisement"
        );
        return reservedNames.contains(normalizedName);
    }


    public static String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;

        Set<String> SMALL_WORDS = new HashSet<>(Arrays.asList(
                "and", "or", "the", "a", "an", "of", "in", "on", "at", "for", "to", "with", "by"
        ));

        String[] words = input.trim().split("\\s+");
        return Arrays.stream(words)
                .map((word) -> {
                    String lower = word.toLowerCase();
                    if (word.equals(words[0]) || !SMALL_WORDS.contains(lower)) {
                        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
                    } else {
                        return lower;
                    }
                })
                .collect(Collectors.joining(" "));
    }
}
