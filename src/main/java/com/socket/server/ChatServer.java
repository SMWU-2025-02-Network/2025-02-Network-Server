package com.socket.server;
// ServerSocket 열기 + 브로드캐스트 + 클라이언트 리스트 관리

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {

    private final int port;

    // 접속 중인 클라이언트 목록
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("[SERVER] 도서관 채팅 서버 시작, 포트: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                // 1) 클라이언트 접속 대기
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] 새 클라이언트 접속: "
                        + clientSocket.getRemoteSocketAddress());

                // 2) 핸들러 생성 후 리스트에 추가
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);

                // 3) 스레드로 실행
                Thread t = new Thread(handler);
                t.start();
            }

        } catch (IOException e) {
            System.out.println("[SERVER] 서버 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 같은 층/방 사용자에게 브로드캐스트
     * - floor, room이 null이면 전송 범위를 결정하기 애매해서 일단 무시
     *   (필요하면 "전체 방송" 로직 따로 추가 가능)
     */
    public void broadcast(SocketMessage message, ClientHandler from) {
        int count = 0;

        Integer msgFloor = message.getFloor();
        String msgRoom = message.getRoom();

        if (msgFloor == null || msgRoom == null) {
            System.out.printf(
                    "[SERVER] floor/room 정보가 없는 메시지 브로드캐스트 요청(type=%s) → 스킵%n",
                    message.getType()
            );
            return;
        }

        for (ClientHandler client : clients) {
            if (client.isSameRoom(msgFloor, msgRoom)) {
                client.sendMessage(message);
                count++;
            }
        }

        System.out.printf(
                "[SERVER] 메시지 브로드캐스트 완료 (type=%s, %d층 %s, 전송 대상 %d명)%n",
                message.getType(),
                msgFloor,
                msgRoom,
                count
        );
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[SERVER] 클라이언트 연결 종료: " + handler);
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer(5050);
        server.start();
    }
}
