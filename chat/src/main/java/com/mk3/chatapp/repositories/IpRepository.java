package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Ip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IpRepository extends JpaRepository<Ip, String> {
}
