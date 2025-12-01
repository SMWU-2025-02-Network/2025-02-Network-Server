package com.socket.sensorPublisher;

import com.google.gson.Gson;
import com.socket.server.SocketMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MultiFloorSensorPublisher {

    private static final Gson gson = new Gson();

    // 층/열람실/센서ID 묶어서 관리
    private record SensorConfig(int floor, String room, String sensorId) {}

    public static void main(String[] args) {

        // 📌 센서 목록: 층/열람실 구조 맞게 필요하면 여기서만 수정하면 됨
        List<SensorConfig> sensors = List.of(
                new SensorConfig(1, "A", "sensor_1A"),
                new SensorConfig(1, "B", "sensor_1B"),
                new SensorConfig(2, "A", "sensor_2A"),
                new SensorConfig(2, "B", "sensor_2B"),
                new SensorConfig(3, "A", "sensor_3A"),
                new SensorConfig(4, "A", "sensor_4A"),
                new SensorConfig(5, "A", "sensor_5A"),
                new SensorConfig(5, "B", "sensor_5B"),
                new SensorConfig(6, "A", "sensor_6A")
        );

        String host = "localhost"; // 서버 IP
        int port = 5050;           // ChatServer 포트

        // ⭐ 센서마다 스레드를 하나씩 돌린다
        for (SensorConfig cfg : sensors) {
            Thread t = new Thread(
                    () -> runSensorLoop(host, port, cfg),
                    "Sensor-" + cfg.sensorId()
            );
            t.start();
        }
    }

    // 한 센서가 5초마다 무한히 데이터를 보내는 루프
    private static void runSensorLoop(String host, int port, SensorConfig cfg) {
        while (true) {
            try (Socket socket = new Socket(host, port)) {
                System.out.printf("[SENSOR %s] 서버 연결: %d층 %s열람실%n",
                        cfg.sensorId(), cfg.floor(), cfg.room());

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                // 1) JOIN (처음 연결될 때 1번)
                SocketMessage joinMsg = SocketMessage.builder()
                        .type("JOIN")
                        .floor(cfg.floor())
                        .room(cfg.room())
                        .sender(cfg.sensorId())
                        .role("SENSOR")
                        .build();

                out.println(gson.toJson(joinMsg));
                out.flush();
                System.out.println("[SENSOR " + cfg.sensorId() + "] JOIN 전송: " + gson.toJson(joinMsg));

                // 2) 5초마다 SENSOR_DATA 전송
                while (true) {
                    double temp = 20 + ThreadLocalRandom.current().nextDouble(0, 5);    // 20~30℃
                    double co2  = 600 + ThreadLocalRandom.current().nextDouble(0, 80);  // 600~680ppm
                    double lux  = 250 + ThreadLocalRandom.current().nextDouble(0, 100); // 250~350 lux

                    SocketMessage dataMsg = SocketMessage.builder()
                            .type("SENSOR_DATA")
                            .temp(temp)
                            .co2(co2)
                            .lux(lux)
                            .build();

                    out.println(gson.toJson(dataMsg));
                    out.flush();

                    System.out.printf("[SENSOR %s] SENSOR_DATA 전송: temp=%.2f, co2=%.0f, lux=%.0f%n",
                            cfg.sensorId(), temp, co2, lux);

                    // 서버에서 오는 응답 읽고 싶으면
                    if (in.ready()) {
                        String line = in.readLine();
                        System.out.println("[FROM SERVER to " + cfg.sensorId() + "] " + line);
                    }

                    // ⏱ 5초 대기
                    Thread.sleep(5000);
                }

            } catch (Exception e) {
                System.out.printf("[SENSOR %s] 연결 오류: %s → 3초 후 재시도%n",
                        cfg.sensorId(), e.getMessage());
                try {
                    Thread.sleep(3000); // 에러 나면 3초 쉬고 다시 연결 시도
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
