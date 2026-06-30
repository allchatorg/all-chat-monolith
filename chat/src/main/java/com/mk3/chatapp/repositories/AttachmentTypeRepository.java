package com.mk3.chatapp.repositories;

import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.models.AttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttachmentTypeRepository extends JpaRepository<AttachmentType, Long> {
    Optional<AttachmentType> findByFileType(AttachmentTypeEnum fileTYpe);
}
