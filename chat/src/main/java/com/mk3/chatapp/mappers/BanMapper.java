package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.models.Ban;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface BanMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(expression = "java(ban.getUser() != null ? ban.getUser().getApplicationUsername() : \"Perma Deleted User\")", target = "username")
    BanResponseDTO toDto(Ban ban);

    Ban toEntity(BanResponseDTO banResponseDTO);
}
