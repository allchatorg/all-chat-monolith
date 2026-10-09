package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.vip.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
