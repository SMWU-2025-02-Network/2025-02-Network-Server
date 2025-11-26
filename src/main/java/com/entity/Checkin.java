package com.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "checkins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Checkin {

    public enum CheckinStatus {
        IN_USE, AWAY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "checkin_time", nullable = false)
    private LocalDateTime checkinTime;

    @Column(name = "checkout_time")
    private LocalDateTime checkoutTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckinStatus status;

    @Column(name = "away_started_at")
    private LocalDateTime awayStartedAt;

    @PrePersist
    protected void onCreate() {
        if (checkinTime == null) checkinTime = LocalDateTime.now();
        if (status == null) status = CheckinStatus.IN_USE;
    }
}

