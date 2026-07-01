package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.ServedAdDto;

/**
 * SPI for serving an ad to a chat user. Implemented out-of-process historically
 * (the chat module POSTed to an external ad service); in the merged monolith the
 * ads module provides an in-process implementation.
 *
 * <p>Defined in the chat module so chat depends only on this interface — the
 * dependency direction stays {@code ads -> chat} (chat never imports ads),
 * avoiding a module cycle. The ads module supplies the bean.
 */
public interface AdServingPort {

    /**
     * Selects and records an ad to serve to the given user.
     *
     * @return the ad to serve, or {@code null} when there is no ad to serve.
     */
    ServedAdDto serveAd(Long userId, String ipAddress);
}
