package com.mk3.chatapp.models;

import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.enums.MimeType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "attachment_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentType extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private AttachmentTypeEnum fileType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attachment_type_mime_types", joinColumns = @JoinColumn(name = "attachment_type_id"))
    @Column(name = "mime_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<MimeType> acceptedMimeTypes = new HashSet<>();

    @Column(name = "max_file_size_bytes")
    private Long maxFileSizeBytes;

    @ManyToMany(fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    @JoinTable(
            name = "attachment_type_tags",
            joinColumns = @JoinColumn(name = "attachment_type_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> availableTags = new HashSet<>();
}