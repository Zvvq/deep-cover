# Game Timer Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 game 模块中实现一个后端权威计时器：房间开始后自动启动聊天阶段倒计时，前端可查询计时器快照，到期后后端标记为过期并通过房间 WebSocket 事件广播。

**Architecture:** room 模块在开始游戏后发布 `RoomStartedEvent`，game 模块监听事件并启动 `CHATTING` 计时器。计时器数据先保存在内存 `ConcurrentHashMap`，后台 `@Scheduled` 扫描所有运行中计时器，到期后保存为 `EXPIRED` 并发布 `TIMER_EXPIRED` 事件。前端用 HTTP 查询当前快照，用已有 `/topic/rooms/{roomCode}/events` 接收关键事件。

**Tech Stack:** Spring Boot 3.5.8、Spring Scheduling、Spring WebSocket `SimpMessagingTemplate`、JUnit 5、AssertJ、Mockito。

---

### Task 1: Timer Service And Repository

**Files:**
- Create: `src/main/java/com/cqie/deepcover/game/enums/GamePhase.java`
- Create: `src/main/java/com/cqie/deepcover/game/enums/TimerStatus.java`
- Create: `src/main/java/com/cqie/deepcover/game/record/GameTimer.java`
- Create: `src/main/java/com/cqie/deepcover/game/record/GameTimerSnapshot.java`
- Create: `src/main/java/com/cqie/deepcover/game/interfaces/GameTimerRepository.java`
- Create: `src/main/java/com/cqie/deepcover/game/interfaces/impl/InMemoryGameTimerRepository.java`
- Create: `src/main/java/com/cqie/deepcover/game/service/GameTimerService.java`
- Test: `src/test/java/com/cqie/deepcover/game/service/GameTimerServiceTest.java`

- [ ] **Step 1: Write failing service tests**

```java
@Test
void startsTimerAndCalculatesRemainingSeconds() {
    GameTimerService service = serviceAt("2026-05-09T03:00:00Z");

    GameTimerSnapshot snapshot = service.startTimer("ABC123", GamePhase.CHATTING, Duration.ofSeconds(300));

    assertThat(snapshot.status()).isEqualTo(TimerStatus.RUNNING);
    assertThat(snapshot.remainingSeconds()).isEqualTo(300);
}
```

- [ ] **Step 2: Verify red**

Run: `mvn '-Dtest=GameTimerServiceTest' test`

Expected: compile failure because game timer classes do not exist.

- [ ] **Step 3: Implement minimal timer records, repository, and service**

Implement immutable timer records with `startedAt`、`endsAt`、`durationSeconds`、`status`，repository stores by `roomCode`，service can start timer, query timer, expire due timers.

- [ ] **Step 4: Verify green**

Run: `mvn '-Dtest=GameTimerServiceTest' test`

Expected: service tests pass.

### Task 2: Room Start Event Integration

**Files:**
- Create: `src/main/java/com/cqie/deepcover/room/record/RoomStartedEvent.java`
- Modify: `src/main/java/com/cqie/deepcover/room/service/RoomService.java`
- Create: `src/main/java/com/cqie/deepcover/game/listener/RoomStartedTimerListener.java`
- Test: `src/test/java/com/cqie/deepcover/game/listener/RoomStartedTimerListenerTest.java`

- [ ] **Step 1: Write failing listener test**

```java
@Test
void startsChattingTimerWhenRoomStartedEventIsHandled() {
    listener.onRoomStarted(new RoomStartedEvent("ABC123"));

    assertThat(timerService.snapshot("ABC123").phase()).isEqualTo(GamePhase.CHATTING);
}
```

- [ ] **Step 2: Verify red**

Run: `mvn '-Dtest=RoomStartedTimerListenerTest' test`

Expected: compile failure because listener and event do not exist.

- [ ] **Step 3: Publish event from RoomService and listen in game module**

After `room.markChatting()` and `roomRepository.save(room)` in `RoomService.startRoom()`，publish `RoomStartedEvent`。`RoomStartedTimerListener` listens with `@EventListener` and starts a 300-second `CHATTING` timer.

- [ ] **Step 4: Verify green**

Run: `mvn '-Dtest=RoomStartedTimerListenerTest,RoomServiceTest' test`

Expected: listener test and existing room tests pass.

### Task 3: Timer Expiration Scheduler And Event Broadcast

**Files:**
- Create: `src/main/java/com/cqie/deepcover/game/config/GameSchedulingConfig.java`
- Create: `src/main/java/com/cqie/deepcover/game/scheduler/GameTimerScheduler.java`
- Create: `src/main/java/com/cqie/deepcover/game/enums/GameEventType.java`
- Create: `src/main/java/com/cqie/deepcover/game/record/GameRoomEvent.java`
- Create: `src/main/java/com/cqie/deepcover/game/record/TimerExpiredPayload.java`
- Create: `src/main/java/com/cqie/deepcover/game/interfaces/GameTimerEventPublisher.java`
- Create: `src/main/java/com/cqie/deepcover/game/interfaces/impl/SimpGameTimerEventPublisher.java`
- Test: extend `src/test/java/com/cqie/deepcover/game/service/GameTimerServiceTest.java`

- [ ] **Step 1: Write failing expiration test**

```java
@Test
void expiresDueTimersAndPublishesTimerExpiredEvent() {
    service.startTimer("ABC123", GamePhase.CHATTING, Duration.ofSeconds(5));
    clock.advanceSeconds(6);

    service.expireDueTimers();

    assertThat(service.snapshot("ABC123").status()).isEqualTo(TimerStatus.EXPIRED);
    assertThat(publisher.lastEvent().type()).isEqualTo(GameEventType.TIMER_EXPIRED);
}
```

- [ ] **Step 2: Verify red**

Run: `mvn '-Dtest=GameTimerServiceTest' test`

Expected: failure because expiration publisher and scheduler support are missing.

- [ ] **Step 3: Implement expiration scan**

Add `GameTimerService.expireDueTimers()`，repository returns running timers，service marks due timers expired and publishes to `/topic/rooms/{roomCode}/events` through `GameTimerEventPublisher`。

- [ ] **Step 4: Add scheduler**

`GameTimerScheduler` runs `expireDueTimers()` every second with `@Scheduled(fixedRate = 1000)`。

- [ ] **Step 5: Verify green**

Run: `mvn '-Dtest=GameTimerServiceTest' test`

Expected: timer service tests pass.

### Task 4: Timer Query API

**Files:**
- Create: `src/main/java/com/cqie/deepcover/game/controller/GameTimerController.java`
- Modify: `src/main/java/com/cqie/deepcover/room/enums/RoomErrorCode.java`
- Modify: `src/main/java/com/cqie/deepcover/room/exception/RoomExceptionHandler.java`
- Test: `src/test/java/com/cqie/deepcover/game/controller/GameTimerControllerTest.java`

- [ ] **Step 1: Write failing controller test**

```java
@Test
void returnsCurrentTimerSnapshot() throws Exception {
    when(gameTimerService.findSnapshot("ABC123", "token-1")).thenReturn(snapshot);

    mockMvc.perform(get("/api/rooms/ABC123/timer").header("X-Player-Token", "token-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.remainingSeconds").value(300));
}
```

- [ ] **Step 2: Verify red**

Run: `mvn '-Dtest=GameTimerControllerTest' test`

Expected: compile failure because controller does not exist.

- [ ] **Step 3: Implement controller and not-found error**

Add `GET /api/rooms/{roomCode}/timer`，service validates player through room module and returns current timer snapshot. Add `TIMER_NOT_FOUND` mapped to HTTP 404.

- [ ] **Step 4: Verify green**

Run: `mvn '-Dtest=GameTimerControllerTest' test`

Expected: controller test passes.

### Task 5: Documentation And Full Verification

**Files:**
- Create: `docs/game-timer-module-api-flow.md`

- [ ] **Step 1: Write Chinese flow document**

Document timer code locations, `GET /api/rooms/{roomCode}/timer` parameters and response, auto-start on room start, scheduled expiration, and `TIMER_EXPIRED` WebSocket event.

- [ ] **Step 2: Run full verification**

Run: `mvn test`

Expected: all tests pass.
