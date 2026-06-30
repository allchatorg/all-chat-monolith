package com.mk3.chatapp.services;

import com.mk3.chatapp.models.Ban;

public interface BanCacheService {
    void addBanEntry(Ban ban);

    boolean isUserBanned(Long userId);

    boolean isIpBanned(String ipAddress);

    Ban getBanByUserId(Long userId);

    void removeBanEntry(Ban ban);

    void removeBanEntryByUserId(Long userId);
}
