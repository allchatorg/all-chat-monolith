package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.pro.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
