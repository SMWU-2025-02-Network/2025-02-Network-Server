package com.scheduler;


import com.dto.SeatUpdateDto;
import com.google.gson.Gson;
import com.service.CheckinService;
import com.socket.server.ChatServer;
import com.socket.server.SocketMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CheckinScheduler {

    private final CheckinService checkinService;
    private final ChatServer chatServer;

    /**
     * 1분마다 AWAY 상태 좌석을 스캔해서
     * 1시간 지난 경우 자동 CHECKOUT 후 SEAT_UPDATE 전송.
     *
     * fixedDelay = 60000 -> 이전 실행 끝난 후 60초 뒤에 다시 실행
     * 테스트 때는 10000(10초) 정도로 줄여도 됨.
     */
    @Scheduled(fixedDelay = 10000)
    public void autoCheckoutAwaySeats() {

        // 1) 자동 checkout + 방별 좌석 상태 목록 가져오기
        List<SeatUpdateDto> updates = checkinService.autoCheckoutAndBuildSeatUpdates();

        // 2) 각 room마다 SEAT_UPDATE 메시지 만들어 브로드캐스트
        for (SeatUpdateDto update : updates){

            // SeatInfoDto -> SocketMessage.SeatInfo 변환
            List<SocketMessage.SeatInfo> seatInfos = update.getSeats().stream()
                    .map(dto -> SocketMessage.SeatInfo.builder()
                            .seatNo(Integer.parseInt(dto.getSeatNo()))
                            .state(dto.getStatus().name())
                            .userId(dto.getUserId() != null ? String.valueOf(dto.getUserId()) : null)
                            .remainSeconds(dto.getRemainSeconds())
                            .build()
                    )
                    .toList();

            SocketMessage msg = SocketMessage.builder()
                    .type("SEAT_UPDATE")
                    .floor(update.getFloor())
                    .room(update.getRoom())
                    .role("SYSTEM")
                    .sender("SYSTEM")
                    .seats(seatInfos)
                    .build();


            // 같은 room의 클라이언트들에게 전송
            chatServer.broadcast(msg, null);
        }
    }
}
