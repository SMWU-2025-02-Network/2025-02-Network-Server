package com.repository;

import com.entity.Checkin;
import com.entity.Seat;
import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {
    // 특정 좌석의 현재 사용 중 row (퇴실 안된 것)
    Optional<Checkin> findFirstBySeatAndCheckoutTimeIsNullOrderByCheckinTimeDesc(Seat seat);

    // 특정 유저가 아직 퇴실하지 않은 row (방에서 중복 체크인 방지용 – 필요하면 사용)
    Optional<Checkin> findFirstByUserAndCheckoutTimeIsNullOrderByCheckinTimeDesc(User user);

    // room 단위 좌석 상태 계산용
    List<Checkin> findBySeat_FloorAndSeat_RoomAndCheckoutTimeIsNull(int floor, String room);

    // AWAY + 1시간 지난 row 자동 퇴실용
    List<Checkin> findByStatusAndCheckoutTimeIsNullAndAwayStartedAtBefore(
            Checkin.CheckinStatus status,
            LocalDateTime awayStartedAtBefore
    );
}