package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.models.identity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = {TagMapper.class})
public interface UserMapper {
    @Mapping(source = "over18", target = "isOver18")
    @Mapping(target = "username", expression = "java(user.getApplicationUsername())")
    @Mapping(target = "banned", source = "banned")
    @Mapping(target = "idVerificationUnderAge", expression =
            "java(user.getIdVerificationStatus() == com.mk3.chatapp.enums.IdVerificationStatus.REJECTED && user.getVerifiedDateOfBirth() != null)")
    @Mapping(target = "vipActive", ignore = true)
    @Mapping(target = "showVipBadge", ignore = true)
    @Mapping(target = "usernameFont", source = "effectiveUsernameFont")
    @Mapping(target = "messageFont", source = "effectiveMessageFont")
    UserDTO toDto(User user);

    /** Account-owner responses include membership and the public badge preference. */
    @Named("owner")
    @InheritConfiguration(name = "toDto")
    @Mapping(target = "vipActive", expression = "java(user.isVipActive())")
    @Mapping(target = "showVipBadge", expression = "java(user.isShowVipBadge())")
    UserDTO toOwnerDto(User user);

    /** Restricted staff detail responses disclose membership, never billing details. */
    @Named("moderation")
    @InheritConfiguration(name = "toDto")
    @Mapping(target = "vipActive", expression = "java(user.isVipActive())")
    UserDTO toModerationDto(User user);

    @Mapping(target = "username", expression = "java(user.getApplicationUsername())")
    @Mapping(target = "usernameFont", source = "effectiveUsernameFont")
    @Mapping(target = "messageFont", source = "effectiveMessageFont")
    UserMinimalDTO toMinimalDto(User user);
}
