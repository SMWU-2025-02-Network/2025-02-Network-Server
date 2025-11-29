package com.repository;

import com.entity.Seat;
import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    //층, 방, 좌석 번호로 찾는 함수
    Optional<Seat> findByFloorAndRoomAndSeatNumber(int floor,
                                                   User.RoomType room,
                                                   String seatNumber);
}