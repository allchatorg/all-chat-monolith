package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    @Query("""
            SELECT COALESCE(SUM(a.size), 0)
            FROM Attachment a
            WHERE a.createdBy = :userId
            AND a.createdAt >= :since
            AND a.deleted = false
            """)
    Long getTotalUploadedFilesSizeSince(@Param("userId") String userId,
                                        @Param("since") Instant since);

    List<Attachment> findAllByCreatedBy(String userId);

}
