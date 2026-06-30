package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.Tag;
import com.mk3.chatapp.repositories.TagRepository;
import com.mk3.chatapp.services.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final TagRepository tagRepository;

    @Override
    public List<Tag> findByIds(List<Long> tagIds) {
        return tagRepository.findAllById(tagIds);
    }

    @Override
    public List<Tag> findAll() {
        return tagRepository.findAll();
    }
}
