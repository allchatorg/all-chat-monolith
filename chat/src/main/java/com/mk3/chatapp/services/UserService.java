package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.ResetPasswordRequestDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

public interface UserService extends UserDetailsService {
    void changePassword(ChangePasswordRequestDTO changePasswordRequestDTO, Principal connectedUser);

    Page<User> searchUsers(UserSearchRequestDTO userSearchRequestDTO);

    Page<UserMinimalDTO> searchClaimedUsersForChat(String query, Principal connectedUser, int page, int size);

    User createGuestUser();

    User findByUsername(String username);

    User findByEmail(String email);

    Optional<User> findOptionalByEmail(String email);

    Optional<User> findOptionalByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User save(User user);

    UserDTO resetPassword(ResetPasswordRequestDTO request);

    void sendEmailVerification(Principal connectedUser);

    UserDTO verifyEmail(String token);

    void sendPhoneVerification(AddPhoneNumberRequest phoneNumberRequest);

    void removeUserPhoneNumber(User user);

    UserDTO verifyPhone(String token);

    User getPrincipal(Principal connectedUser);

    UserDTO changeUsername(String username, Principal connectedUser);

    void requestEmailUpdate(RequestEmailUpdateDTO request, Principal connectedUser);

    UserDTO verifyEmailUpdate(VerifyEmailUpdateDTO request, Principal connectedUser);

    void updateMarketingPreferences(UpdateMarketingPreferencesDTO request, Principal connectedUser);

    User findById(Long id);

    void updateAge(boolean isOver18, Principal connectedUser);

    UserDTO getUserInfo(Principal connectedUser);

    List<TagDTO> updateBlurredContent(List<Long> tagIds, Principal connectedUser);

    void deleteAccount(User user, DeleteAccountRequest deleteAccountRequest);

    void deleteOwnAccount(User user, DeleteAccountRequest deleteAccountRequest);

    Long incrementTotalUploadedFilesSize(Long size, User user);

    String updateUserDisplayColor(String color);

    void deleteStaleAccounts();

    void blockUser(Long userId, Principal connectedUser);

    void unblockUser(Long userId, Principal connectedUser);

    List<UserMinimalDTO> getBlockedUsers(Principal connectedUser);

    List<User> findStaffMembers();

    void quarantineUser(User user);

    void submitModeratorApplication(Principal connectedUser,
                                    ModeratorApplicationRequest request);
}
