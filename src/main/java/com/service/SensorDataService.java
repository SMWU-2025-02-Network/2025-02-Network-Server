package com.service;

import com.entity.SensorData;
import com.entity.User;
import com.repository.SensorDataRepository;
import com.socket.server.ChatServer;
import com.socket.server.SocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final ChatServer chatServer;

    // room별 최신 스냅샷 캐시
    private final Map<RoomKey, SensorSnapshot> latestSnapshotMap = new ConcurrentHashMap<>();

    public void handleSensorData(SocketMessage msg) {

        int floor = msg.getFloor();
        String room = msg.getRoom();
        String sender = msg.getSender();

        // -----------------------------
        // 1) DB에 3행 INSERT
        // -----------------------------
        User.RoomType roomEnum = User.RoomType.valueOf(room); // "A" -> RoomType.A

        SensorData tempRow = SensorData.builder()
                .floor(floor)
                .room(roomEnum)
                .type(SensorData.SensorType.TEMP)
                .value(msg.getTemp().floatValue())
                .sender(sender)
                .build();

        SensorData co2Row = SensorData.builder()
                .floor(floor)
                .room(roomEnum)
                .type(SensorData.SensorType.CO2)
                .value(msg.getCo2().floatValue())
                .sender(sender)
                .build();

        SensorData luxRow = SensorData.builder()
                .floor(floor)
                .room(roomEnum)
                .type(SensorData.SensorType.LUX)
                .value(msg.getLux().floatValue())
                .sender(sender)
                .build();

        sensorDataRepository.saveAll(List.of(tempRow, co2Row, luxRow));

        // -----------------------------
        // 2) 최신값 캐시 업데이트
        // -----------------------------
        RoomKey key = new RoomKey(floor, room);
        SensorSnapshot snapshot = new SensorSnapshot(
                msg.getTemp(),
                msg.getCo2(),
                msg.getLux(),
                LocalDateTime.now()
        );
        latestSnapshotMap.put(key, snapshot);

        // -----------------------------
        // 3) DASHBOARD_UPDATE 브로드캐스트
        // -----------------------------
        SocketMessage dashboardMsg = SocketMessage.builder()
                .type("DASHBOARD_UPDATE")
                .floor(floor)
                .room(room)
                .role("SYSTEM")
                .sender("SYSTEM")
                .temp(snapshot.temp())
                .co2(snapshot.co2())
                .lux(snapshot.lux())
                .build();

        chatServer.broadcast(dashboardMsg, null);

        log.info("[SENSOR] {}층 {}실 센서 데이터 수신 및 브로드캐스트 완료", floor, room);
    }

    // 필요하면 나중에 대시보드에서 최근값 조회용으로 쓸 수 있음
    public SensorSnapshot getLatestSnapshot(int floor, String room) {
        return latestSnapshotMap.get(new RoomKey(floor, room));
    }

    // ====== record / helper 클래스들 ======

    public record RoomKey(int floor, String room) { }

    public record SensorSnapshot(Double temp, Double co2, Double lux,
                                 LocalDateTime updatedAt) { }
}