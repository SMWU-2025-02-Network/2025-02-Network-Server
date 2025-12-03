package com.service;

import com.dto.SeatInfoDto;
import com.dto.SeatUpdateDto;
import com.entity.Checkin;
import com.entity.Checkin.CheckinStatus;
import com.entity.Checkin.SeatStatus;
import com.entity.Seat;
import com.entity.User;
import com.exception.AlreadyCheckedInException;
import com.repository.CheckinRepository;
import com.repository.SeatRepository;
import com.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j

public class CheckinService {

    private final CheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;

    //----공통 함수----
    private Seat getSeat(int floor, String room, int seatNo) {

        String seatNumber = String.valueOf(seatNo);

        // 로그 찍어서 지금 실제로 뭐가 들어오는지도 확인
        log.info("[getSeat] floor={}, room='{}', seatNo={}", floor, room, seatNo);

        // 3,4,6층 같이 room 구분이 없는 경우 (room = null 이나 공백)
        if (room == null || room.isBlank()) {
            return seatRepository.findByFloorAndSeatNumber(floor, seatNumber)
                    .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));
        }

        // 1,2,5층 A/B 구역 있는 경우
        User.RoomType roomType = User.RoomType.valueOf(room);  // "A" / "B"

        return seatRepository.findByFloorAndRoomAndSeatNumber(floor, roomType, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));
    }


    private Checkin getActiveCheckin(Seat seat) {
        return checkinRepository
                .findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .orElseThrow(() -> new IllegalStateException("현재 사용 중인 좌석이 아닙니다."));
    }

    // CHECKIN
    public void checkin(int floor, String room, int seatNo, String userId) {

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Seat seat = getSeat(floor, room, seatNo);

        //사용자가 이미 체크인한 좌석이 있다면
        var existingOpt =
                checkinRepository.findFirstByUserAndCheckoutTimeIsNullOrderByCheckinTimeDesc(user);

        if (existingOpt.isPresent()) {
            throw new AlreadyCheckedInException("이미 이용중인 좌석이 있습니다.");
        }


        // 해당 좌석에 누군가 앉아있을 경우 예외처리
        checkinRepository.findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .ifPresent(active -> {
                    throw new IllegalStateException("이미 사용중인 좌석입니다.");
                });

        // 같은 user + seat 의 마지막 row를 가져와서 재사용할지 결정
        Optional<Checkin> lastOpt =
                checkinRepository.findFirstByUserAndSeatOrderByCheckinTimeDesc(user, seat);

        Checkin checkin;
        if (lastOpt.isPresent()) {

            // 기존 row 재사용 (id 그대로 유지)
            checkin = lastOpt.get();
            checkin.startNewSession();     // 상태/시간만 다시 세팅
        } else {
            // 없는 경우에만 새 row 생성
            checkin = Checkin.builder()
                    .user(user)
                    .seat(seat)
                    .build();
            checkin.startNewSession();
            checkinRepository.save(checkin);
        }
    }

    // AWAY_START
    public void startAway(int floor, String room, int seatNo, String userId) {

        Seat seat = getSeat(floor, room, seatNo);

        Checkin checkin = checkinRepository
                .findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .orElseThrow(() -> new IllegalStateException("현재 사용 중인 좌석이 아닙니다."));

        // 본인 좌석인지 확인
        if (!checkin.getUser().getId().equals(Long.parseLong(userId))) {
            throw new IllegalStateException("이 좌석의 사용자가 아닙니다.");
        }

        checkin.startAway();
    }

    //AWAY_BACK
    public void backFromAway(int floor, String room, int seatNo, String userId) {

        Seat seat = getSeat(floor, room, seatNo);

        Checkin checkin = checkinRepository
                .findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .orElseThrow(() -> new IllegalStateException("현재 사용 중인 좌석이 아닙니다."));

        if (!checkin.getUser().getId().equals(Long.parseLong(userId))) {
            throw new IllegalStateException("이 좌석의 사용자가 아닙니다.");
        }

        checkin.backFromAway();
    }

    // CHECKOUT
    public void checkout(int floor, String room, int seatNo, String userId) {

        Seat seat = getSeat(floor, room, seatNo);

        Checkin checkin = checkinRepository
                .findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .orElseThrow(() -> new IllegalStateException("현재 사용 중인 좌석이 아닙니다."));

        if (!checkin.getUser().getId().equals(Long.parseLong(userId))) {
            throw new IllegalStateException("이 좌석의 사용자가 아닙니다.");
        }

        checkin.checkout();
    }


    //좌석 상태 1개 계산 (Seat 기준)
    @Transactional(readOnly = true)
    public SeatStatus getSeatStatus(Seat seat) {
        return checkinRepository
                .findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .map(c -> c.getStatus() == CheckinStatus.AWAY ? SeatStatus.AWAY : SeatStatus.IN_USE)
                .orElse(SeatStatus.EMPTY);
    }

    // room 기준 좌석 상태 목록 (SEAT_UPDATE에서 사용)
    @Transactional(readOnly = true)
    public List<SeatInfoDto> getSeatStatusesByRoom(int floor, String room) {

        List<Checkin> active;

        // 6층처럼 room 없는 층
        if (room == null || room.isBlank()) {
            active = checkinRepository.findBySeat_FloorAndCheckoutTimeIsNull(floor);
        } else {
            User.RoomType roomType = User.RoomType.valueOf(room); // "A","B"
            active = checkinRepository
                    .findBySeat_FloorAndSeat_RoomAndCheckoutTimeIsNull(floor, roomType);
        }

        // seat 별 최신 체크인만 남기기
        Map<Long, Checkin> latestBySeatId = new HashMap<>();
        for (Checkin c : active) {
            Long seatId = c.getSeat().getId();
            Checkin prev = latestBySeatId.get(seatId);
            if (prev == null || prev.getCheckinTime().isBefore(c.getCheckinTime())) {
                latestBySeatId.put(seatId, c);
            }
        }

        return latestBySeatId.values().stream()
                .map(c -> {
                    Seat seat = c.getSeat();
                    SeatStatus seatStatus =
                            (c.getStatus() == CheckinStatus.AWAY)
                                    ? SeatStatus.AWAY
                                    : SeatStatus.IN_USE;

                    // 남은 시간 계산 (필요 없으면 0으로 둬도 됨)
                    int remainSeconds = 0;
                    if (seatStatus == SeatStatus.IN_USE) {
                        var end = c.getCheckinTime().plusHours(2);
                        remainSeconds = (int) java.time.Duration
                                .between(LocalDateTime.now(), end)
                                .getSeconds();
                        if (remainSeconds < 0) remainSeconds = 0;
                    } else if (seatStatus == SeatStatus.AWAY && c.getAwayStartedAt() != null) {
                        var end = c.getAwayStartedAt().plusHours(1);
                        remainSeconds = (int) java.time.Duration
                                .between(LocalDateTime.now(), end)
                                .getSeconds();
                        if (remainSeconds < 0) remainSeconds = 0;
                    }

                    return new SeatInfoDto(
                            seat.getId(),
                            seat.getSeatNumber(),
                            seatStatus,
                            c.getUser().getId(),
                            remainSeconds
                    );
                })
                .collect(Collectors.toList());
    }




    /**
     * AWAY 상태에서 기준 시간 지난 체크인들을 자동으로 checkout 처리하고,
     * 층/room 별로 최신 좌석 상태 목록을 묶어서 반환한다.
     */
    public List<SeatUpdateDto> autoCheckoutAndBuildSeatUpdates() {

        //외출 시간 1분 기준 (테스트용)
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);

        // 외출 시간 1시간 기준
        // LocalDateTime threshold = LocalDateTime.now().minusHours(1);

        var outdated = checkinRepository
                .findByStatusAndCheckoutTimeIsNullAndAwayStartedAtBefore(
                        CheckinStatus.AWAY,
                        threshold
                );

        // 영향을 받은 (floor, room)을 key 로 저장
        Set<String> affectedRooms = new HashSet<>();
        for (Checkin c : outdated) {
            c.checkout(); // 실제 퇴실 처리
            String key = c.getSeat().getFloor() + "|" + c.getSeat().getRoom(); // Seat 필드명에 맞게
            affectedRooms.add(key);
        }

        // 방별로 최신 좌석 상태 계산해서 SeatUpdateDto로 묶기
        List<SeatUpdateDto> result = new ArrayList<>();
        for (String key : affectedRooms) {
            String[] parts = key.split("\\|");
            int floor = Integer.parseInt(parts[0]);
            String room = parts[1];

            List<SeatInfoDto> seats = getSeatStatusesByRoom(floor, room);

            result.add(
                    SeatUpdateDto.builder()
                            .floor(floor)
                            .room(room)
                            .seats(seats)
                            .build()
            );
        }
        return result;
    }
}
