package com.socket.server;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocketMessage {

    // 공통 필드 (JOIN, CHAT, SYSTEM, DASHBOARD_UPDATE 등에서 사용)
    private String type;      // "JOIN", "CHAT", "DASHBOARD_UPDATE" ...
    private Integer floor;
    private String room;
    private String role;      // "USER", "ADMIN", "SENSOR", "SYSTEM"
    private String sender;    // 닉네임 / 센서ID / SYSTEM
    private String msg;       // 채팅 내용이나 시스템 메시지

    // 좌석/체크인 관련
    private Integer seatNo;
    private String userId;

    // 센서 관련
    private Double temp;
    private Double co2;
    private Double lux;

    // 좌석 목록 (SEAT_UPDATE 에서 사용)
    private List<SeatInfo> seats;

    /**
     * 좌석 상태 정보 (클라이언트 SocketMessage.SeatInfo 와 동일 구조)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeatInfo {
        private Integer seatNo;        // 좌석 번호
        private String state;          // "EMPTY", "IN_USE", "AWAY"
        private String userId;         // 해당 좌석 사용자 ID (없으면 null)
        private Integer remainSeconds; // 남은 시간(초) — 필요 없으면 null
    }
}
