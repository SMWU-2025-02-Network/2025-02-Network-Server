package com.socket.server;
// 테스트용 콘솔 클라이언트

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ConsoleChatClient {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        String host = "localhost";  // 서버가 다른 PC면 IP로 변경
        int port = 5050;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("[CLIENT] 서버에 연결됨: " + host + ":" + port);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            // --- 사용자 정보 입력 ---
            System.out.print("층 번호 입력 (예: 3): ");
            int floor = Integer.parseInt(scanner.nextLine().trim());

            System.out.print("구역 입력 (A / B, 없으면 그냥 엔터): ");
            String roomInput = scanner.nextLine().trim();
            String room = roomInput.isEmpty() ? null : roomInput.toUpperCase();

            System.out.print("역할 입력 (USER / ADMIN): ");
            String role = scanner.nextLine().trim().toUpperCase();

            System.out.print("닉네임 입력: ");
            String nickname = scanner.nextLine().trim();

            // --- 서버에서 오는 메시지 수신 스레드 ---
            Thread receiver = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        SocketMessage msg = gson.fromJson(line, SocketMessage.class);
                        String type = msg.getType();

                        if ("CHAT".equalsIgnoreCase(type)) {
                            // 일반 채팅
                            System.out.printf("[CHAT][%dF-%s][%s] %s : %s%n",
                                    msg.getFloor(), msg.getRoom(),
                                    msg.getRole(), msg.getSender(),
                                    msg.getMsg());
                        }
                        else if ("SYSTEM".equalsIgnoreCase(type)) {
                            // 입장/퇴장 알림 등
                            System.out.printf("[SYSTEM][%dF-%s] %s%n",
                                    msg.getFloor(), msg.getRoom(),
                                    msg.getMsg());
                        }
                        else if ("DASHBOARD_UPDATE".equalsIgnoreCase(type)) {
                            // 센서 대시보드 업데이트
                            System.out.printf("[DASHBOARD][%dF-%s] TEMP=%.1f°C, CO2=%.0fppm, LUX=%.0f lux%n",
                                    msg.getFloor(), msg.getRoom(),
                                    msg.getTemp(), msg.getCo2(), msg.getLux());
                        }
                        else if ("SEAT_UPDATE".equalsIgnoreCase(type)) {
                            // 좌석 상태 업데이트
                            System.out.printf("[SEAT_UPDATE][%dF-%s] 좌석 정보 %d개 업데이트%n",
                                    msg.getFloor(), msg.getRoom(),
                                    msg.getSeats() != null ? msg.getSeats().size() : 0);
                        }
                        else {
                            // 디버깅용
                            System.out.println("[UNKNOWN] " + line);
                        }
                    }

                } catch (IOException e) {
                    System.out.println("[CLIENT] 서버와의 연결 종료");
                }
            });

            receiver.setDaemon(true);
            receiver.start();

            // --- JOIN 메시지 1번 전송 ---
            SocketMessage join = SocketMessage.builder()
                    .type("JOIN")
                    .role(role)
                    .floor(floor)
                    .room(room)          // "A"/"B"/null
                    .sender(nickname)
                    .msg(nickname + " 입장")
                    .build();

            out.println(gson.toJson(join));

            // --- 채팅 루프 ---
            System.out.println("채팅 시작! 종료하려면 /quit 입력");
            while (true) {
                String text = scanner.nextLine();
                if ("/quit".equalsIgnoreCase(text)) {
                    break;
                }

                // 1) 만약 사용자가 JSON을 직접 입력했다면 → 그대로 서버로 전송
                if (text.trim().startsWith("{")) {
                    out.println(text);
                    continue;
                }

                // 2) 그 외에는 기존처럼 CHAT으로 보내기
                SocketMessage msg = SocketMessage.builder()
                        .type("CHAT")
                        .role("USER")
                        .floor(floor)
                        .room(room)
                        .sender(nickname)
                        .msg(text)
                        .build();

                out.println(gson.toJson(msg));
            }

        } catch (IOException e) {
            System.out.println("[CLIENT] 오류: " + e.getMessage());
        }
    }
}
