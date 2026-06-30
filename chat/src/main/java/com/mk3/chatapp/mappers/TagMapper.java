package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.models.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TagMapper {
    TagDTO toDto(Tag tag);

    Tag toEntity(TagDTO tagDTO);
}