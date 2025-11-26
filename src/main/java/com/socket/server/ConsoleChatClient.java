package com.socket.server;
//테스트용 콘솔 클라이언트

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
            int floor = Integer.parseInt(scanner.nextLine());

            System.out.print("구역/열람실 이름 입력 (예: A열람실): ");
            String room = scanner.nextLine();

            System.out.print("역할 입력 (USER / ADMIN): ");
            String role = scanner.nextLine().trim().toUpperCase();

            System.out.print("닉네임 입력: ");
            String nickname = scanner.nextLine();

            // --- 서버에서 오는 메시지 수신 스레드 ---
            Thread receiver = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        ChatMessage msg = gson.fromJson(line, ChatMessage.class);

                        String prefix = String.format("[RECV][%dF-%s]",
                                msg.getFloor(),
                                msg.getRoom());

                        if ("SYSTEM".equalsIgnoreCase(msg.getType())) {
                            // 시스템 메시지 (입장/퇴장 등)
                            System.out.printf("%s[SYSTEM] %s%n",
                                    prefix,
                                    msg.getContent());
                        } else {
                            // 일반 채팅 메시지
                            System.out.printf("%s[%s] %s : %s%n",
                                    prefix,
                                    msg.getRole(),      // USER / ADMIN
                                    msg.getSender(),    // 닉네임
                                    msg.getContent());  // 내용
                        }
                    }
                } catch (IOException e) {
                    System.out.println("[CLIENT] 서버와의 연결 종료");
                }
            });

            receiver.setDaemon(true);
            receiver.start();

            // --- JOIN 메시지 1번 전송 ---
            ChatMessage join = new ChatMessage(
                    "JOIN",
                    role,
                    floor,
                    room,
                    nickname,
                    nickname + " 입장"
            );
            out.println(gson.toJson(join));

            // --- 채팅 루프 ---
            System.out.println("채팅 시작! 종료하려면 /quit 입력");
            while (true) {
                String text = scanner.nextLine();
                if ("/quit".equalsIgnoreCase(text)) {
                    break;
                }

                ChatMessage chat = new ChatMessage(
                        "CHAT",
                        role,
                        floor,
                        room,
                        nickname,
                        text
                );
                out.println(gson.toJson(chat));
            }

        } catch (IOException e) {
            System.out.println("[CLIENT] 오류: " + e.getMessage());
        }
    }
}

