package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.NotificationDTO;
import com.mk3.chatapp.models.Notification;
import com.mk3.chatapp.utils.DateTimeMapperUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateTimeMapperUtil.class)
public interface NotificationMapper {

    @Mapping(source = "readAt", target = "readAt", qualifiedByName = "instantToString")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "instantToString")
    NotificationDTO toDto(Notification notification);
}
