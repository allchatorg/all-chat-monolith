package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.Ip;
import com.mk3.chatapp.repositories.IpRepository;
import com.mk3.chatapp.services.IpService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class IpServiceImpl implements IpService {

    private final IpServiceImpl self;
    private final IpRepository ipRepository;

    @Override
    public RequiredVerificationEnum getRequiredVerification(String targetIp) {
        var ip = self.getIp(targetIp);
        return Objects.isNull(ip) ? RequiredVerificationEnum.NONE : ip.getRequiredVerification();
    }

    @Override
    public Ip flagBannedIp(Role role, String ip) {
        var requiredVerification = self.determineRequiredVerification(role, getRequiredVerification(ip));
        var ipEntry = Ip.builder()
                .ip(ip)
                .requiredVerification(requiredVerification)
                .build();

        return self.saveIp(ipEntry);
    }

    @Cacheable(value = "ipCache", key = "#ip")
    @Override
    public Ip getIp(String ip) {
        return ipRepository.findById(ip).orElse(null);
    }

    @CachePut(value = "ipCache", key = "#ip.ip")
    @Override
    public Ip saveIp(Ip ip) {
        return ipRepository.save(ip);
    }

    @CacheEvict(value = "ipCache", key = "#ip")
    @Override
    public void deleteIp(String ip) {
        ipRepository.deleteById(ip);
    }

    private RequiredVerificationEnum determineRequiredVerification(
            Role role,
            RequiredVerificationEnum previousRequiredVerification
    ) {
        if (previousRequiredVerification == RequiredVerificationEnum.PHONE) {
            return RequiredVerificationEnum.PHONE;
        }

        if (previousRequiredVerification == RequiredVerificationEnum.EMAIL) {
            return RequiredVerificationEnum.PHONE;
        }

        return switch (role) {
            case UNCLAIMED_USER, GUEST -> RequiredVerificationEnum.EMAIL;
            case USER -> RequiredVerificationEnum.PHONE;
            default -> RequiredVerificationEnum.NONE;
        };
    }
}

