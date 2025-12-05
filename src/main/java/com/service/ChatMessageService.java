package com.service;

import com.repository.ChatMessageRepository;
import com.repository.UserRepository;
import com.entity.ChatMessage;
import com.entity.User;
import com.socket.server.SocketMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public void saveChat(SocketMessage msg) {

        // 1. sender(login_id)로 User 조회
        User user = null;
        if (msg.getSender() != null) {
            user = userRepository.findByLoginId(msg.getSender())
                    .orElse(null); // 못 찾으면 null 허용
        }

        // 2. room 문자열 -> ENUM 변환
        User.RoomType roomType = null;
        if (msg.getRoom() != null) {
            try {
                roomType = User.RoomType.valueOf(msg.getRoom().toUpperCase());
            } catch (Exception e) {
                System.out.println("[WARN] Invalid room: " + msg.getRoom());
            }
        }

        // 3. role 문자열 -> ENUM 변환
        User.RoleType roleType = null;
        if (msg.getRole() != null) {
            try {
                roleType = User.RoleType.valueOf(msg.getRole().toUpperCase());
            } catch (Exception e) {
                System.out.println("[WARN] Invalid role: " + msg.getRole());
            }
        }

        // 4. username을 nickname으로 사용
        String nickname = (user != null) ? user.getUsername() : msg.getSender();

        // 5. floor 처리
        int floor = (msg.getFloor() != null) ? msg.getFloor() : 0;

        // 6. ChatMessage 엔티티 생성
        ChatMessage chatMessage = ChatMessage.builder()
                .floor(floor)
                .room(roomType)
                .role(roleType)
                .nickname(nickname)
                .user(user)
                .message(msg.getMsg())
                .build();

        chatMessageRepository.save(chatMessage);
    }
}
