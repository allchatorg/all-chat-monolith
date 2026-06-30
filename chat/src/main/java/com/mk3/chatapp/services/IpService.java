package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.Ip;

public interface IpService {
    RequiredVerificationEnum getRequiredVerification(String targetIp);

    Ip flagBannedIp(Role role, String ip);

    Ip getIp(String ip);

    Ip saveIp(Ip ip);

    void deleteIp(String ip);
}
