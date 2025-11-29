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
        IN_USE, //자리 사용중
        AWAY //자리 비움(외출)
    }

    //좌석 상태 (옵션)
    public enum SeatStatus {
        EMPTY,
        IN_USE,
        AWAY
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

    /*JPA가 DB에 Insert 하기 직전에 자동으로 실행되는 콜백 함수
    - checkinTime이 null이면 → 현재 시간으로 자동 세팅
    - status가 null이면 → 기본값 IN_USE로 자동 설정
     */
    @PrePersist
    protected void onCreate() {
        if (checkinTime == null) checkinTime = LocalDateTime.now();
        if (status == null) status = CheckinStatus.IN_USE;
    }

    //사용자가 처음 Checkin 할 때 상태 초기화하는 메서드
    public void markCheckin() {
        this.status = CheckinStatus.IN_USE; //status: IN_USE
        this.checkinTime = LocalDateTime.now(); //현재 시간
        this.awayStartedAt = null;
        this.checkoutTime = null;
    }

    //외출 시작할때 사용할 메서드
    public void startAway() {
        this.status = CheckinStatus.AWAY; //status: AWAY
        this.awayStartedAt = LocalDateTime.now();
    }

    //외출에서 돌아왔을 때의 메서드
    public void backFromAway() {
        this.status = CheckinStatus.IN_USE; //status: IN_USE
        this.awayStartedAt = null;
    }

    //체크아웃 했을 때의 메서드
    public void checkout() {
        this.checkoutTime = LocalDateTime.now();
    }

    // 새 체크인 세션 시작(기존 row 재사용도 이 메서드로 처리)
    public void startNewSession() {
        this.status = CheckinStatus.IN_USE;
        this.checkinTime = LocalDateTime.now();
        this.awayStartedAt = null;
        this.checkoutTime = null;
    }
}

