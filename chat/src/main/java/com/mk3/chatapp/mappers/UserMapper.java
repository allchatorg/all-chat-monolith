package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.models.identity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {TagMapper.class})
public interface UserMapper {
    @Mapping(source = "over18", target = "isOver18")
    @Mapping(target = "username", expression = "java(user.getApplicationUsername())")
    @Mapping(target = "banned", source = "banned")
    UserDTO toDto(User user);

    @Mapping(target = "username", expression = "java(user.getApplicationUsername())")
    UserMinimalDTO toMinimalDto(User user);
}
