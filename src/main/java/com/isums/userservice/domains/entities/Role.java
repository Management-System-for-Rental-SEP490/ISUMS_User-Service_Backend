package com.isums.userservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.Set;
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

    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();
}
