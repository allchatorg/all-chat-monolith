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
    @Mapping(target = "proActive", ignore = true)
    @Mapping(target = "showProBadge", ignore = true)
    @Mapping(target = "usernameFont", source = "effectiveUsernameFont")
    @Mapping(target = "messageFont", source = "effectiveMessageFont")
    UserDTO toDto(User user);

    /** Only authentication/account-owner responses may disclose hidden membership. */
    @Named("owner")
    @InheritConfiguration(name = "toDto")
    @Mapping(target = "proActive", expression = "java(user.isProActive())")
    @Mapping(target = "showProBadge", expression = "java(user.isShowProBadge())")
    UserDTO toOwnerDto(User user);

    @Mapping(target = "username", expression = "java(user.getApplicationUsername())")
    @Mapping(target = "usernameFont", source = "effectiveUsernameFont")
    @Mapping(target = "messageFont", source = "effectiveMessageFont")
    UserMinimalDTO toMinimalDto(User user);
}
