package com.mk3.chatapp.models;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.core.annotation.Order;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "tags")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Tag extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "restricted_to_adults")
    private boolean restrictedToAdults;

    @ManyToMany(mappedBy = "availableTags")
    @Builder.Default
    private Set<AttachmentType> attachmentTypes = new HashSet<>();
}
