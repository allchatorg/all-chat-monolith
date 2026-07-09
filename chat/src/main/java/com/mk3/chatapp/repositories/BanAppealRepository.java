package com.mk3.chatapp.repositories;

import com.mk3.chatapp.enums.BanAppealStatus;
import com.mk3.chatapp.models.BanAppeal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BanAppealRepository extends JpaRepository<BanAppeal, Long> {

    Optional<BanAppeal> findByBan_Id(Long banId);

    Optional<BanAppeal> findByBan_IdAndStatusIn(Long banId, Collection<BanAppealStatus> statuses);

    Optional<BanAppeal> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    Page<BanAppeal> findAllByStatus(BanAppealStatus status, Pageable pageable);

    Page<BanAppeal> findAllByStatusIn(Collection<BanAppealStatus> statuses, Pageable pageable);

    List<BanAppeal> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
