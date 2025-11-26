package com.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seat_floor_room_number",
                columnNames = {"floor", "room", "seat_number"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    private User.RoomType room;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;
}

