package com.mk3.chatapp.services;

import com.mk3.chatapp.models.Tag;

import java.util.List;

public interface TagService {
    List<Tag> findByIds(List<Long> tagIds);

    List<Tag> findAll();
}
