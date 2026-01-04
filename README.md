# 📚 도서관 환경 모니터링 네트워크 (Library Environment Monitoring Network)
## 2025-2 네트워크 프로젝트 · Server

본 레포지토리는 **2025-2 네트워크 프로젝트의 서버(Server) 애플리케이션**입니다.  
Java 기반으로 구현되었으며, **TCP Socket 통신을 중심으로 클라이언트(UI)와 센서 퍼블리셔를 연결**하는
중앙 서버 역할을 담당합니다.

---

## 1. 프로젝트 개요

중앙도서관 1~6층을 대상으로,  
**좌석 QR 체크인 + 익명 채팅 + 환경 센서 대시보드**를 제공하는 PC 기반 네트워크 시스템입니다.

이 서버는 다음 역할을 수행합니다.

- 좌석 체크인 / 체크아웃 상태 관리
- AREA(층/열람실) 단위 익명 채팅방 운영
- 관리자 통합 채팅방 운영
- 환경 센서 데이터 수신 및 실시간 브로드캐스트
- 네트워크 장애 및 비정상 상태 감지

---

## 2. 서비스 시나리오 요약

### ✔ 서버 기준 동작 흐름

1. 클라이언트(UI) 또는 센서 퍼블리셔가 서버에 Socket 연결
2. 메시지에 포함된 **층 / AREA 정보** 기반으로 처리 분기
3. 서버는 다음을 중앙에서 관리
   - 좌석 사용 상태
   - AREA별 채팅방
   - 관리자 채팅방
   - 센서 데이터 최신 스냅샷
4. 상태 변경 시 관련 클라이언트에 **실시간 전파**

---

## 3. 네트워크 AREA 구성

- 중앙도서관 **1~6층**
- **1, 2, 5층** → 열람실 2개 → AREA 2개
- **3, 4, 6층** → 열람실 미분리 → AREA 1개

### 네트워크 구조 포인트

- L3 코어 스위치 기반 중앙 집중형 구조
- AREA 단위 VLAN / 서브넷 분리
- 모든 트래픽은 서버를 중심으로 라우팅
- 센서 데이터는 AREA 정보와 함께 서버로 전달됨

---

## 4. 시스템 아키텍처 (Server 관점)

### 🖥 Server 역할

- 멀티 클라이언트 Socket 연결 관리
- 사용자 / 관리자 역할 구분
- AREA 기반 채팅 메시지 라우팅
- 센서 데이터 수집 및 브로드캐스트
- 스케줄러 기반 상태 점검
- 네트워크 장애 발생 시 관리자 알림

---

## 5. 주요 기능 (Server)

- ✔ 좌석 체크인 / 체크아웃 처리
- ✔ 자리 비움 타이머 로직 관리
- ✔ AREA별 익명 채팅방 관리
- ✔ 관리자 통합 채팅방
- ✔ 센서 데이터 실시간 처리
- ✔ 네트워크 / 센서 장애 감지

---

## 6. 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 / 21 |
| Framework | Spring Boot |
| Network | TCP Socket |
| Build Tool | Gradle |
| Config | application.properties |
| IDE | IntelliJ IDEA |

---

## 7. 프로젝트 구조 (Server)

```text
src/
 ├─ main/
 │   ├─ java/
 │   │   └─ com/
 │   │       ├─ dto/                 # 데이터 전송 객체 (Socket 메시지, 응답 DTO)
 │   │       ├─ entity/              # 서버 도메인 엔티티
 │   │       ├─ exception/           # 커스텀 예외 정의
 │   │       ├─ repository/          # 데이터 접근 계층
 │   │       ├─ scheduler/           # 주기적 작업 처리
 │   │       ├─ service/             # 비즈니스 로직
 │   │       └─ socket/
 │   │           ├─ sensorPublisher/                 # 가상 IoT 센서 퍼블리셔
 │   │           │   └─ MultiFloorSensorPublisher.java
 │   │           │
 │   │           ├─ server/                          # TCP Socket 서버 핵심 로직
 │   │           │   ├─ ChatServer.java               # 서버 메인 소켓
 │   │           │   ├─ ClientHandler.java            # 클라이언트 연결 처리 (멀티스레드)
 │   │           │   ├─ ConsoleChatClient.java        # 콘솔 기반 테스트용 클라이언트
 │   │           │   └─ SocketMessage.java            # 통신 메시지 DTO
 │   │           │
 │   │           └─ Smwu202502NetworkApplication.java # Spring Boot 서버 실행 진입점
 │   │
 │   └─ resources/
 │       └─ application.properties
 │
 └─ test/
     └─ java/
         └─ com/                                    # 테스트 코드

```
---

## 8. 실행 방법

### 1) 서버 실행

1. 서버 레포지토리를 클론합니다.
2. application.properties 설정 파일을 확인합니다.
3. 아래 방법 중 하나로 서버를 실행합니다.

- IntelliJ IDEA에서 Smwu202502NetworkApplication 실행
- Gradle을 사용하여 서버 실행

⚠️ 본 서버는 UI Client 및 Sensor Publisher가 연결되어야  
채팅, 좌석 관리, 센서 데이터 기능이 정상 동작합니다.

---

## 9. 연관 레포지토리

- **UI Client**  
  Java Swing 기반 사용자 / 관리자 UI 레포지토리

- **Sensor Publisher**  
  가상 IoT 센서 데이터 송신용 레포지토리

- **Network Diagram / Documentation**  
  네트워크 설계도 및 시스템 구조 문서

---

## 10. 향후 확장 아이디어

- MQTT 브로커 연동을 통한 메시지 처리 구조 개선
- 실제 IoT 센서 장비와의 연계
- 환경 데이터 시계열 분석 및 시각화
- 웹 또는 모바일 클라이언트 확장

---

## 👩‍💻 개발자

- 네트워크 서버 아키텍처 설계
- TCP Socket 기반 서버 구현
- 상태 관리 및 메시지 라우팅 처리
- 센서 데이터 수집 및 처리 로직 구현
