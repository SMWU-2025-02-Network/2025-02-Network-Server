package com;

import com.socket.server.ChatServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class Smwu202502NetworkApplication {

	public static void main(String[] args) {
		SpringApplication.run(Smwu202502NetworkApplication.class, args);
	}

	@Bean
	public CommandLineRunner startChatServer(ChatServer chatServer) {
		return args -> {
			Thread chatServerThread = new Thread(() -> {
				log.info("[ChatServerRunner] ChatServer 실행 시작");
				chatServer.start();
			}, "chat-server-thread");

			chatServerThread.setDaemon(true); // 스프링 종료 시 같이 종료되도록
			chatServerThread.start();
		};
	}
}
