package com.socket.server;
//프로토콜/소켓용 DTO

import lombok.*;
import java.util.List;
import com.dto.SeatInfoDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocketMessage {

    // 공통 필드
    private String type;    // CONNECT, ROLE_SELECT, JOIN_ROOM, CHAT, CHECKIN, SENSOR_DATA, ...
    private String role;    // USER, ADMIN, SENSOR
    private Integer floor;  // 층 (관리자 전용 등일 때는 null도 가능)
    private String room;    // "A", "B" 또는 null(층 전체)
    private String sender;  // userId 또는 sensorId

    // CHAT 전용
    private String msg;     // 채팅 텍스트 (이름을 프로토콜의 "msg"랑 맞추기)

    // CHECKIN / AWAY_START / AWAY_BACK / CHECKOUT 전용
    private Integer seatNo;
    private String userId;  // CHECKIN 관련 ID (sender와 같을 수도 있음)

    // SENSOR_DATA / DASHBOARD_UPDATE 전용
    private Double temp;
    private Double lux;
    private Double co2;

    // ERROR / ALERT 전용
    private String message;   // 에러/알림 메시지
    private Integer code;     // 에러 코드 등

    // SEAT_UPDATE 전용
    // type = "SEAT_UPDATE" 일 때, 같은 room의 좌석 상태 목록을 담아 보냄
    private List<SeatInfoDto> seats;
}
