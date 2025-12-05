package com.dto;
//좌석 상태 DTO (SEAT_UPDATE 에서 쓸 것)

import com.entity.Checkin.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeatInfoDto {
    private Long seatId;          // seat PK
    private String seatNo;        // UI에 보여줄 좌석 번호
    private SeatStatus status;    // EMPTY / IN_USE / AWAY
    private Long userId;          // 사용자가 없으면 null
    private Integer remainSeconds;
}
