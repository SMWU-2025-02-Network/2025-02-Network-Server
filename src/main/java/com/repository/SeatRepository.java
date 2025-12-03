package com.repository;

import com.entity.Seat;
import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    // 1, 2, 5층 같이 A/B 구역이 있는 층
    Optional<Seat> findByFloorAndRoomAndSeatNumber(
            int floor,
            User.RoomType room,
            String seatNumber
    );

    // 3, 4, 6층 같이 구역이 없는 층 (room IS NULL)
    Optional<Seat> findByFloorAndSeatNumber(
            int floor,
            String seatNumber
    );
}
