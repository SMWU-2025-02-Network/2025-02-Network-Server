package com.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    // 내부 ENUM
    public enum RoleType {
        USER, ADMIN
    }

    public enum RoomType {
        A, B
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType role;

    @Column(name = "floor")
    private Integer floor; // 관리자 담당 층 (일반 유저는 null)

    @Column(name = "room")
    private String room;   // 관리자 담당 구역 (일반 유저는 null)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

