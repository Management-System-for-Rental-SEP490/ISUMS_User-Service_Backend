package com.isums.userservice.domains.entities;

import com.isums.common.i18n.TranslationMap;
import com.isums.common.i18n.TranslationMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "roles",
        indexes = {
                @Index(name = "idx_roles_code", columnList = "code", unique = true)
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Role {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition="uuid")
    private UUID id;

    @Column(nullable=false, unique=true)
    private String code;

    @Column(columnDefinition="text")
    private String description;

    @Column(name = "description_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap descriptionTranslations;
}
