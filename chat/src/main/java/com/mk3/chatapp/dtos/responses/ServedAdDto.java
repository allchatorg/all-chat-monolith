package com.mk3.chatapp.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServedAdDto {
    private Long id;
    private String title;
    private String textContent;
    private String imageUrl;
    private String videoUrl;
    private String format;
}
