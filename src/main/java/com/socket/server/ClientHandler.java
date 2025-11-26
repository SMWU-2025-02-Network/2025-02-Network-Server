package com.socket.server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private static final Gson gson = new Gson();

    private final Socket socket;
    private final ChatServer server;

    private PrintWriter out;
    private BufferedReader in;

    // 이 클라이언트의 정보 저장(옵션)
    private int floor;
    private String room;
    private String nickname;
    private String role;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    // 서버가 이 클라이언트에게 메시지를 보낼 때 사용
    public void sendMessage(ChatMessage message) {
        if (out == null) return;
        String json = gson.toJson(message);
        out.println(json);
        out.flush();
    }

    //같은 방, 같은 열람실인지 구분하는 함수
    public boolean isSameRoom(int floor, String room) {
        if (this.room == null || room == null) return false;
        return this.floor == floor && this.room.equals(room);
    }


    @Override
    public void run() {
        try {
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[RAW FROM CLIENT] " + line);

                try {
                    ChatMessage msg = gson.fromJson(line, ChatMessage.class);

                    if ("JOIN".equalsIgnoreCase(msg.getType())) {
                        // JOIN 메시지: 클라이언트 메타정보 저장
                        this.floor = msg.getFloor();
                        this.room = msg.getRoom();
                        this.nickname = msg.getSender();
                        this.role = msg.getRole();

                        System.out.printf("[JOIN] %s(%s) - %d층 %s%n",
                                nickname, role, floor, room);

                        // 입장 알림
                        ChatMessage notice = new ChatMessage(
                                "SYSTEM",
                                "SYSTEM",
                                floor,
                                room,
                                "SYSTEM",
                                nickname + " 님이 입장했습니다."
                        );
                        server.broadcast(notice, this);

                    } else if ("CHAT".equalsIgnoreCase(msg.getType())) {
                        // 일반 채팅 메시지
                        server.broadcast(msg, this);
                    } else {
                        // 확장용 (SENSOR, LEAVE 등 나중에 추가 가능)
                        System.out.println("[INFO] 처리되지 않은 type: " + msg.getType());
                    }

                } catch (JsonSyntaxException ex) {
                    System.out.println("[ERROR] JSON 파싱 실패: " + ex.getMessage());
                }
            }

        } catch (IOException e) {
            System.out.println("[ClientHandler] 통신 오류: " + e.getMessage());
        } finally {
            // 🔽 여기서 퇴장 SYSTEM 메시지 브로드캐스트
            if (nickname != null && room != null) {
                ChatMessage leaveMsg = new ChatMessage(
                        "SYSTEM",
                        "SYSTEM",
                        floor,
                        room,
                        "SYSTEM",
                        nickname + " 님이 퇴장했습니다."
                );
                server.broadcast(leaveMsg, this);
            }

            server.removeClient(this);
            try {
                socket.close();
            } catch (IOException ignore) {}
        }
    }


    @Override
    public String toString() {
        return "ClientHandler{" +
                "floor=" + floor +
                ", room='" + room + '\'' +
                ", nickname='" + nickname + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}

