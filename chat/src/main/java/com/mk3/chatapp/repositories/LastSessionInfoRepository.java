package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.LastSessionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastSessionInfoRepository extends JpaRepository<LastSessionInfo, Long> {
}
