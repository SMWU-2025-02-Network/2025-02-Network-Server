package com.socket.server;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String type;    // JOIN, CHAT, SENSOR 등
    private String role;    // USER, ADMIN, SENSOR
    private int floor;      // 층
    private String room;    // 구역
    private String sender;  // 닉네임 또는 센서 ID
    private String content; // 메시지 내용

    @Override
    public String toString() {
        return "ChatMessage{" +
                "type='" + type + '\'' +
                ", role='" + role + '\'' +
                ", floor=" + floor +
                ", room='" + room + '\'' +
                ", sender='" + sender + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
