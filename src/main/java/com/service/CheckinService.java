package com.service;

import com.dto.SeatInfoDto;
import com.entity.Checkin;
import com.entity.Checkin.CheckinStatus;
import com.entity.Checkin.SeatStatus;
import com.entity.Seat;
import com.entity.User;
import com.repository.CheckinRepository;
import com.repository.SeatRepository;
import com.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckinService {

    private final CheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;

    //----공통 함수----
    private Seat getSeat(int floor, String room, int seatNo) {
        return seatRepository.findByFloorAndRoomAndSeatNumber(floor, room, seatNo)
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

        // 같은 유저가 아직 체크아웃 안 한 게 있으면 정리 (정책에 따라 다르게 처리 가능)
        checkinRepository.findFirstByUserAndCheckoutTimeIsNullOrderByCheckinTimeDesc(user)
                .ifPresent(Checkin::checkout);

        // 해당 좌석에 누군가 앉아있을 경우 예외처리
        checkinRepository.findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(seat)
                .ifPresent(active -> {
                    throw new IllegalStateException("이미 사용중인 좌석입니다.");
                });

        Checkin checkin = Checkin.builder()
                .user(user)
                .seat(seat)
                .status(Checkin.CheckinStatus.IN_USE)
                .checkinTime(LocalDateTime.now())
                .build();

        checkinRepository.save(checkin);
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
        // 1) 아직 퇴실 안 한 체크인들 가져오기
        List<Checkin> active = checkinRepository
                .findBySeat_FloorAndSeat_RoomAndCheckoutTimeIsNull(floor, room);

        // 2) seat 기준 최신 row만 남기기
        Map<Long, Checkin> latestBySeatId = new HashMap<>();
        for (Checkin c : active) {
            Long seatId = c.getSeat().getId();
            Checkin prev = latestBySeatId.get(seatId);
            if (prev == null || prev.getCheckinTime().isBefore(c.getCheckinTime())) {
                latestBySeatId.put(seatId, c);
            }
        }

        // 3) SeatInfoDto로 변환
        return latestBySeatId.values().stream()
                .map(c -> {
                    Seat seat = c.getSeat();
                    SeatStatus seatStatus =
                            (c.getStatus() == CheckinStatus.AWAY) ? SeatStatus.AWAY : SeatStatus.IN_USE;

                    return new SeatInfoDto(
                            seat.getId(),
                            seat.getSeatNumber(),
                            seatStatus,
                            c.getUser().getId()
                    );
                })
                .collect(Collectors.toList());
    }

    // AWAY 1시간 이상 → 자동 CHECKOUT (스케줄러에서 호출)
    public List<SeatInfoDto> autoCheckoutAndGetUpdatedSeats() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);

        var outdated = checkinRepository
                .findByStatusAndCheckoutTimeIsNullAndAwayStartedAtBefore(
                        CheckinStatus.AWAY,
                        threshold
                );

        // 영향을 받은 (floor, room) set 만들기
        Set<String> affectedRooms = new HashSet<>();
        for (Checkin c : outdated) {
            c.checkout();
            String key = c.getSeat().getFloor() + "|" + c.getSeat().getRoom(); // Seat 필드명에 맞게 수정
            affectedRooms.add(key);
        }

        // 방별로 좌석 상태 다시 계산해서 한 번에 반환
        List<SeatInfoDto> result = new ArrayList<>();
        for (String key : affectedRooms) {
            String[] parts = key.split("\\|");
            int floor = Integer.parseInt(parts[0]);
            String room = parts[1];

            result.addAll(getSeatStatusesByRoom(floor, room));
        }
        return result;
    }
}
