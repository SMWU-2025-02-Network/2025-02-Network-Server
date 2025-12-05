package com;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

// 센서 시뮬레이터 테스트 클라이언트
public class SensorTestClient {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 5050;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("[CLIENT] 서버 연결 완료");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // 1) JOIN: 2층 A, 센서 sensor_2A
            String joinJson = """
                    {"type":"JOIN","floor":2,"room":"A","sender":"sensor_2A","role":"SENSOR"}
                    """;
            out.println(joinJson);
            out.flush();
            System.out.println("[CLIENT] JOIN 전송: " + joinJson);

            // 2) SENSOR_DATA 한 번 보내보기
            String sensorJson = """
                    {"type":"SENSOR_DATA","temp":23.5,"co2":640,"lux":300}
                    """;
            out.println(sensorJson);
            out.flush();
            System.out.println("[CLIENT] SENSOR_DATA 전송: " + sensorJson);

            // 3) 서버에서 오는 응답(대시보드 업데이트) 한두 줄만 읽어보기 (옵션)
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[FROM SERVER] " + line);
            }
        }
    }
}
