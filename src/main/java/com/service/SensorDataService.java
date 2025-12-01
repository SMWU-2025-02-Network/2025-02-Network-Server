package com.service;

import com.entity.SensorData;
import com.entity.User;
import com.repository.SensorDataRepository;
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

    // room별 최신 스냅샷 캐시
    private final Map<RoomKey, SensorSnapshot> latestSnapshotMap = new ConcurrentHashMap<>();

    // 반환 타입을 SensorSnapshot으로 설정
    public SensorSnapshot handleSensorData(SocketMessage msg) {

        int floor = msg.getFloor();
        String room = msg.getRoom();
        String sender = msg.getSender();

        // 1) DB에 3행 INSERT
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

        // 2) 최신값 캐시 업데이트
        RoomKey key = new RoomKey(floor, room);
        SensorSnapshot snapshot = new SensorSnapshot(
                msg.getTemp(),
                msg.getCo2(),
                msg.getLux(),
                LocalDateTime.now()
        );
        latestSnapshotMap.put(key, snapshot);

        log.info("[SENSOR] {}층 {}실 센서 데이터 수신 및 저장 완료", floor, room);

        //브로드캐스트는 여기서 하지 않고, 호출한 쪽에서 하도록 스냅샷만 반환
        return snapshot;
    }

    public SensorSnapshot getLatestSnapshot(int floor, String room) {
        return latestSnapshotMap.get(new RoomKey(floor, room));
    }

    public record RoomKey(int floor, String room) { }

    public record SensorSnapshot(Double temp, Double co2, Double lux,
                                 LocalDateTime updatedAt) { }
}
