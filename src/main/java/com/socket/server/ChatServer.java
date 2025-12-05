package com.socket.server;
// ServerSocket 열기 + 브로드캐스트 + 클라이언트 리스트 관리

import com.service.ChatMessageService;
import com.service.CheckinService;
import com.service.SensorDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class ChatServer {

    private final int port = 5050;
    private final ChatMessageService chatMessageService;
    private final CheckinService checkinService;
    private final SensorDataService sensorDataService;


    // 접속 중인 클라이언트 목록
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public ChatServer(ChatMessageService chatMessageService, CheckinService checkinService
            , SensorDataService sensorDataService) {
        this.chatMessageService = chatMessageService;
        this.checkinService = checkinService;
        this.sensorDataService = sensorDataService;
    }

    public void start() {
        log.info("[SERVER] 도서관 채팅 서버 시작, 포트: {}", port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                // 1) 클라이언트 접속 대기
                Socket clientSocket = serverSocket.accept();
                log.info("[SERVER] 새 클라이언트 접속: {}", clientSocket.getRemoteSocketAddress());

                // 2) 핸들러 생성 후 리스트에 추가
                ClientHandler handler =
                        new ClientHandler(clientSocket, this, chatMessageService,
                                checkinService, sensorDataService);
                clients.add(handler);

                // 3) 스레드로 실행
                Thread t = new Thread(handler);
                t.start();
            }

        } catch (IOException e) {
            log.error("[SERVER] 서버 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 같은 층/방 사용자에게 브로드캐스트
     * - floor, room이 null이면 전송 범위를 결정하기 애매해서 일단 무시
     * (필요하면 "전체 방송" 로직 따로 추가 가능)
     */
    public void broadcast(SocketMessage message, ClientHandler from) {
        int count = 0;

        // 0. 관리자 전용 채팅 처리 ─────────────────────────
        if ("ADMIN_CHAT".equals(message.getType())) {
            for (ClientHandler client : clients) {
                // ClientHandler 안에 role 필드가 있고,
                // 로그인/JOIN 때 setRole("ADMIN"/"USER") 해준다는 전제
                if ("ADMIN".equals(client.getRole())) {
                    client.sendMessage(message);
                    count++;
                }
            }

            log.info("[SERVER] ADMIN_CHAT 브로드캐스트 완료 (전송 대상 관리자 {}명)", count);
            return; // 일반 채팅/좌석/대시보드 브로드캐스트로 내려가지 않도록 바로 종료
        }

        // 1. 그 외(type != ADMIN_CHAT) 기존 로직 ─────────────────────────
        Integer msgFloor = message.getFloor();
        String msgRoom = message.getRoom();

        if (msgFloor == null) {   // floor만 필수
            log.warn("[SERVER] floor 정보가 없는 메시지 브로드캐스트 요청(type={}) → 스킵",
                    message.getType());
            return;
        }

        for (ClientHandler client : clients) {
            if (client.isSameRoom(msgFloor, msgRoom)) {
                client.sendMessage(message);
                count++;
            }
        }

        log.info("[SERVER] 메시지 브로드캐스트 완료 (type={}, {}층 {}, 전송 대상 {}명)",
                message.getType(), msgFloor, msgRoom, count);
    }
}