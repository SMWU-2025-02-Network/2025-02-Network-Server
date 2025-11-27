package com.socket.server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.service.ChatMessageService;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private static final Gson gson = new Gson();

    private final Socket socket;
    private final ChatServer server;
    private final ChatMessageService chatMessageService;

    private PrintWriter out;
    private BufferedReader in;

    // 이 클라이언트의 정보 저장
    private int floor;
    private String room;
    private String nickname; // sender(userId) 개념
    private String role;     // USER / ADMIN / SENSOR

    public ClientHandler(Socket socket, ChatServer server,
                         ChatMessageService chatMessageService) {
        this.socket = socket;
        this.server = server;
        this.chatMessageService = chatMessageService;
    }

    // 서버가 이 클라이언트에게 메시지를 보낼 때 사용
    public void sendMessage(SocketMessage message) {
        if (out == null) return;
        String json = gson.toJson(message);
        out.println(json);
        out.flush();
    }

    // 같은 방(층+구역)인지 구분하는 함수
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
                    SocketMessage msg = gson.fromJson(line, SocketMessage.class);
                    if (msg == null || msg.getType() == null) {
                        System.out.println("[WARN] type 없는 메시지 무시");
                        continue;
                    }

                    String type = msg.getType().toUpperCase();

                    // JOIN / JOIN_ROOM : 클라이언트 메타정보 등록
                    if ("JOIN".equals(type) || "JOIN_ROOM".equals(type)) {

                        Integer msgFloor = msg.getFloor();
                        this.floor = (msgFloor != null) ? msgFloor : -1;
                        this.room = msg.getRoom();         // "A" / "B" / null
                        this.nickname = msg.getSender();   // 로그인 아이디 or 닉네임
                        this.role = msg.getRole();         // USER / ADMIN / SENSOR

                        System.out.printf("[JOIN] %s(%s) - %d층 %s%n",
                                nickname, role, floor, room);

                        // 입장 SYSTEM 알림
                        SocketMessage notice = SocketMessage.builder()
                                .type("SYSTEM")
                                .role("SYSTEM")
                                .floor(this.floor)
                                .room(this.room)
                                .sender("SYSTEM")
                                .msg(nickname + " 님이 입장했습니다.")
                                .build();

                        server.broadcast(notice, this);

                    }
                    // CHAT : 같은 방 사용자에게 브로드캐스트
                    else if ("CHAT".equals(type)) {

                        // 1) msg에 기본 정보가 비어 있으면, 이 클라이언트에 저장된 값으로 채우기
                        if (msg.getFloor() == null) {
                            msg.setFloor(this.floor);
                        }
                        if (msg.getRoom() == null) {
                            msg.setRoom(this.room);
                        }
                        if (msg.getSender() == null) {
                            msg.setSender(this.nickname);  // sender = 로그인 아이디
                        }
                        if (msg.getRole() == null) {
                            msg.setRole(this.role);        // USER / ADMIN
                        }

                        // 2) DB 저장 (SocketMessage -> ChatMessage 변환 + save)
                        try {
                            chatMessageService.saveChat(msg);
                        } catch (Exception e) {
                            System.out.println("[ERROR] 채팅 로그 DB 저장 실패: " + e.getMessage());
                            // 실패해도 채팅 자체는 흘려보내고 싶으면 그냥 진행
                        }

                        // 3) 동일 방 유저에게 브로드캐스트
                        server.broadcast(msg, this);
                    }

                    // TODO: CHECKIN / AWAY_START / AWAY_BACK / CHECKOUT / SENSOR_DATA 등 확장
                    else {
                        System.out.println("[INFO] 처리되지 않은 type: " + msg.getType());
                    }

                } catch (JsonSyntaxException ex) {
                    System.out.println("[ERROR] JSON 파싱 실패: " + ex.getMessage());
                }
            }

        } catch (IOException e) {
            System.out.println("[ClientHandler] 통신 오류: " + e.getMessage());
        } finally {
            // 퇴장 SYSTEM 메시지 브로드캐스트
            if (nickname != null && room != null) {
                SocketMessage leaveMsg = SocketMessage.builder()
                        .type("SYSTEM")
                        .role("SYSTEM")
                        .floor(this.floor)
                        .room(this.room)
                        .sender("SYSTEM")
                        .msg(nickname + " 님이 퇴장했습니다.")
                        .build();

                server.broadcast(leaveMsg, this);
            }

            //server.removeClient(this);
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
