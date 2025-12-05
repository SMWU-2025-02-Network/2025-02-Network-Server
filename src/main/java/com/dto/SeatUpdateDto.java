package com.dto;

//방 단위 좌석 업데이트

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatUpdateDto {

    private int floor;
    private String room;
    private List<SeatInfoDto> seats;   // 해당 room의 좌석 상태 목록
}
