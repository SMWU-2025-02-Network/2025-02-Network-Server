package com.socket.server;

import com.exception.AlreadyCheckedInException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.service.ChatMessageService;
import com.service.CheckinService;
import com.dto.SeatInfoDto;
import com.service.SensorDataService;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private static final Gson gson = new Gson();

    private final Socket socket;
    private final ChatServer server;
    private final ChatMessageService chatMessageService;
    private final CheckinService checkinService;
    private final SensorDataService sensorDataService;

    private PrintWriter out;
    private BufferedReader in;

    // 이 클라이언트의 정보 저장
    private int floor;
    private String room;
    private String nickname; // sender(userId) 개념
    private String role;     // USER / ADMIN / SENSOR

    public ClientHandler(Socket socket, ChatServer server,
                         ChatMessageService chatMessageService,
                         CheckinService checkinService, SensorDataService sensorDataService) {
        this.socket = socket;
        this.server = server;
        this.chatMessageService = chatMessageService;
        this.checkinService = checkinService;
        this.sensorDataService = sensorDataService;
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

        // 1) 층 다르면 out
        if (this.floor != floor) return false;

        // "null" 문자열도 room 없음으로 취급
        boolean thisNoRoom  = (this.room == null
                || this.room.isBlank()
                || "null".equalsIgnoreCase(this.room));
        boolean msgNoRoom   = (room == null
                || room.isBlank()
                || "null".equalsIgnoreCase(room));

        // 2) 둘 다 room 없음 → 층 같으면 같은 방
        if (thisNoRoom && msgNoRoom) {
            return true;
        }

        // 3) 둘 다 room 있음 → floor+room 같을 때만
        if (!thisNoRoom && !msgNoRoom) {
            return this.room.equals(room);
        }

        // 4) 한쪽만 room 있음 → 다른 방
        return false;
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
                        this.room = msg.getRoom();         // 3,4,6층은 null
                        this.nickname = msg.getSender();   // 로그인 아이디
                        this.role = msg.getRole();         // USER / ADMIN / SENSOR

                        System.out.printf("[JOIN] %s(%s) - %d층 %s%n",
                                nickname, role, floor, room);

                        // 1) 입장 SYSTEM 알림
                        SocketMessage notice = SocketMessage.builder()
                                .type("SYSTEM")
                                .role("SYSTEM")
                                .floor(this.floor)
                                .room(this.room)   // 3,4,6층이면 null
                                .sender("SYSTEM")
                                .msg(nickname + " 님이 입장했습니다.")
                                .build();

                        server.broadcast(notice, this);

                        // 2) 현재 좌석 상태를 이 클라이언트에게만 전송
                        if (this.floor > 0) {
                            sendSeatUpdateToOneClient(this.floor, this.room);
                        }
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

                    else if ("ADMIN_CHAT".equals(type)) {

                        // 기본 정보 비어 있으면 this.xxx 로 채우기
                        if (msg.getFloor() == null)  msg.setFloor(this.floor);
                        if (msg.getRoom() == null)   msg.setRoom(this.room);   // 방 정보 필요 없으면 그대로 둬도 됨
                        if (msg.getSender() == null) msg.setSender(this.nickname);
                        if (msg.getRole() == null)   msg.setRole(this.role);   // "ADMIN"

                        // (원하면 DB 저장도 가능)
                        try {
                            chatMessageService.saveChat(msg);   // 관리자인 것도 role 로 같이 저장
                        } catch (Exception e) {
                            System.out.println("[ERROR] ADMIN_CHAT DB 저장 실패: " + e.getMessage());
                        }

                        // ChatServer 쪽에서 ADMIN 들에게만 뿌려줌
                        server.broadcast(msg, this);
                    }

                    // CHECKIN 처리
                    else if ("CHECKIN".equals(type)) {
                        handleCheckin(msg);
                    }

                    // AWAY_START 처리
                    else if ("AWAY_START".equals(type)) {
                        handleAwayStart(msg);
                    }

                    // AWAY_BACK 처리
                    else if ("AWAY_BACK".equals(type)) {
                        handleAwayBack(msg);
                    }

                    // CHECKOUT 처리
                    else if ("CHECKOUT".equals(type)) {
                        handleCheckout(msg);
                    }

                    //SENSOR_DATA 처리
                    else if ("SENSOR_DATA".equals(type)) {

                        // 1) 기본 정보 채우기
                        if (msg.getFloor() == null) msg.setFloor(this.floor);
                        if (msg.getRoom() == null) msg.setRoom(this.room);
                        if (msg.getSender() == null) msg.setSender(this.nickname);
                        if (msg.getRole() == null) msg.setRole(this.role);

                        System.out.println("[SERVER] SENSOR_DATA 수신:"
                                + " floor=" + msg.getFloor()
                                + ", room=" + msg.getRoom()
                                + ", sender=" + msg.getSender()
                                + ", temp=" + msg.getTemp()
                                + ", co2=" + msg.getCo2()
                                + ", lux=" + msg.getLux());

                        // 2) 센서 데이터 DB/캐시 처리
                        SensorDataService.SensorSnapshot snapshot =
                                sensorDataService.handleSensorData(msg);

                        // 3) DASHBOARD_UPDATE 만들어서 같은 room에 브로드캐스트
                        SocketMessage dashboardMsg = SocketMessage.builder()
                                .type("DASHBOARD_UPDATE")
                                .floor(msg.getFloor())
                                .room(msg.getRoom())
                                .role("SYSTEM")
                                .sender("SYSTEM")
                                .temp(snapshot.temp())
                                .co2(snapshot.co2())
                                .lux(snapshot.lux())
                                .build();

                        server.broadcast(dashboardMsg, null);
                    }

                    else if ("SEAT_STATUS_REQUEST".equals(type)) {
                        handleSeatStatusRequest(msg);
                    }


                    else {
                        System.out.println("[INFO] 처리되지 않은 type: " + msg.getType());
                    }

                } catch (JsonSyntaxException ex) {
                    System.out.println("[ERROR] JSON 파싱 실패: " + ex.getMessage());
                } catch (Exception ex) {
                    // CHECKIN 등에서 터지는 모든 예외를 여기서 잡고,
                    //  연결은 유지하면서 로그만 남기기
                    System.out.println("[ERROR] 메시지 처리 중 예외 발생: " + ex.getMessage());
                    ex.printStackTrace();
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
    // =====================================
    // 아래부터 좌석 관련 헬퍼 메서드들
    // =====================================

    /**
     * CHECKIN 처리 로직
     * 1. 메시지에 floor/room/userId 기본값 없으면 this.xxx 로 채움
     * 2. CheckinService.checkin() 호출
     * 3. 같은 room 사용자들에게 SEAT_UPDATE 브로드캐스트
     */
    private void handleCheckin(SocketMessage msg) {

        if (msg.getFloor() == null) msg.setFloor(this.floor);
        if (msg.getRoom() == null) msg.setRoom(this.room);
        if (msg.getUserId() == null) msg.setUserId(this.nickname);

        int floor = msg.getFloor();
        String room = msg.getRoom();
        int seatNo = msg.getSeatNo();
        String userId = msg.getUserId();

        try {
            checkinService.checkin(floor, room, seatNo, userId);
        } catch (Exception ex) {
            System.out.println("[ERROR] CHECKIN 처리 중 예외 발생: " + ex.getMessage());
            ex.printStackTrace();

            SocketMessage err = SocketMessage.builder()
                    .type("ERROR")
                    .role("SYSTEM")
                    .floor(this.floor)
                    .room(this.room)
                    .sender("SYSTEM")
                    .msg(ex.getMessage())
                    .build();

            this.sendMessage(err);
            return;   // 에러 났으면 SEAT_UPDATE 보내지 말고 종료
        }

        // 정상 체크인 된 경우에만 SEAT_UPDATE 브로드캐스트
        sendSeatUpdateToRoom(floor, room);
    }


    /**
     * AWAY_START 처리 로직
     * - CheckinService.startAway() 호출 후 SEAT_UPDATE 브로드캐스트
     */
    private void handleAwayStart(SocketMessage msg) {

        if (msg.getFloor() == null) msg.setFloor(this.floor);
        if (msg.getRoom() == null) msg.setRoom(this.room);
        if (msg.getUserId() == null) msg.setUserId(this.nickname);

        int floor = msg.getFloor();
        String room = msg.getRoom();
        int seatNo = msg.getSeatNo();
        String userId = msg.getUserId();

        checkinService.startAway(floor, room, seatNo, userId);

        sendSeatUpdateToRoom(floor, room);
    }

    /**
     * AWAY_BACK 처리 로직
     * - CheckinService.backFromAway() 호출 후 SEAT_UPDATE 브로드캐스트
     */
    private void handleAwayBack(SocketMessage msg) {

        if (msg.getFloor() == null) msg.setFloor(this.floor);
        if (msg.getRoom() == null) msg.setRoom(this.room);
        if (msg.getUserId() == null) msg.setUserId(this.nickname);

        int floor = msg.getFloor();
        String room = msg.getRoom();
        int seatNo = msg.getSeatNo();
        String userId = msg.getUserId();

        checkinService.backFromAway(floor, room, seatNo, userId);

        sendSeatUpdateToRoom(floor, room);
    }

    /**
     * CHECKOUT 처리 로직
     * - CheckinService.checkout() 호출 후 SEAT_UPDATE 브로드캐스트
     */
    private void handleCheckout(SocketMessage msg) {

        if (msg.getFloor() == null) msg.setFloor(this.floor);
        if (msg.getRoom() == null) msg.setRoom(this.room);
        if (msg.getUserId() == null) msg.setUserId(this.nickname);

        int floor = msg.getFloor();
        String room = msg.getRoom();
        int seatNo = msg.getSeatNo();
        String userId = msg.getUserId();

        checkinService.checkout(floor, room, seatNo, userId);

        sendSeatUpdateToRoom(floor, room);
    }

    /**
     * SEAT_UPDATE 메시지를 만들어 같은 room의 모든 클라이언트에게 브로드캐스트
     */
    private void sendSeatUpdateToRoom(int floor, String room) {

        List<SeatInfoDto> dtoList = checkinService.getSeatStatusesByRoom(floor, room);

        System.out.println("[SEAT_UPDATE] floor=" + floor + ", room=" + room
                + ", seats size=" + dtoList.size());

        List<SocketMessage.SeatInfo> seatInfos = dtoList.stream()
                .map(dto -> SocketMessage.SeatInfo.builder()
                        .seatNo(Integer.parseInt(dto.getSeatNo()))
                        .state(dto.getStatus().name())
                        .userId(dto.getUserId() != null ? String.valueOf(dto.getUserId()) : null)
                        .remainSeconds(dto.getRemainSeconds())
                        .build()
                )
                .toList();

        SocketMessage updateMsg = SocketMessage.builder()
                .type("SEAT_UPDATE")
                .floor(floor)
                .room(room)
                .role("SYSTEM")
                .sender("SYSTEM")
                .seats(seatInfos)
                .build();

        server.broadcast(updateMsg, null);
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

    // ClientHandler 안에 추가
    private void sendSeatUpdateToOneClient(int floor, String room) {

        // 1) 현재 room의 좌석 상태 가져오기
        List<SeatInfoDto> dtoList = checkinService.getSeatStatusesByRoom(floor, room);

        System.out.println("[SEAT_UPDATE-ONE] floor=" + floor + ", room=" + room
                + ", seats size=" + dtoList.size());

        // 2) SeatInfoDto -> SocketMessage.SeatInfo 변환
        List<SocketMessage.SeatInfo> seatInfos = dtoList.stream()
                .map(dto -> SocketMessage.SeatInfo.builder()
                        .seatNo(Integer.parseInt(dto.getSeatNo()))
                        .state(dto.getStatus().name())
                        .userId(dto.getUserId() != null ? String.valueOf(dto.getUserId()) : null)
                        .remainSeconds(dto.getRemainSeconds())
                        .build()
                )
                .toList();

        // 3) 메시지 생성
        SocketMessage updateMsg = SocketMessage.builder()
                .type("SEAT_UPDATE")
                .floor(floor)
                .room(room)
                .role("SYSTEM")
                .sender("SYSTEM")
                .seats(seatInfos)
                .build();

        // 4) 이 클라이언트에게만 전송
        this.sendMessage(updateMsg);
    }

    private void handleSeatStatusRequest(SocketMessage msg) {

        if (msg.getFloor() == null) msg.setFloor(this.floor);
        if (msg.getRoom() == null) msg.setRoom(this.room);

        int floor = msg.getFloor();
        String room = msg.getRoom();

        System.out.println("[SEAT_STATUS_REQUEST] floor=" + floor + ", room=" + room);

        sendSeatUpdateToOneClient(floor, room);
    }

    public String getRole() {
        return role;
    }

}
