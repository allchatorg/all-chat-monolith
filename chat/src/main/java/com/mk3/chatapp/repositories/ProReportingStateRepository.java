package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.pro.ProReportingState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ProReportingStateRepository extends JpaRepository<ProReportingState, Long> {
    @Modifying
    @Query(value = "insert into pro_reporting_state (id, last_synchronized_at, synchronized_once, incomplete, synchronization_failed) " +
            "values (1, :checkpoint, false, false, false) on conflict (id) do nothing", nativeQuery = true)
    void initialize(@Param("checkpoint") Instant checkpoint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProReportingState s where s.id = 1")
    Optional<ProReportingState> lockState();
}
