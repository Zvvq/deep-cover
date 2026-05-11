# Game Timer 模块接口与流程说明

这份文档按当前代码整理，用来说明 game timer 模块怎么在后端做权威计时、前端怎么查询计时器、时间到后怎么通过 WebSocket 通知房间内玩家。

当前版本只做单一计时器能力，不直接处理投票、淘汰、胜负判断。后续投票模块可以接 `TIMER_EXPIRED` 事件继续做状态流转。

## 代码位置

| 类型 | 路径 | 作用 |
| --- | --- | --- |
| Controller | `src/main/java/com/cqie/deepcover/game/controller/GameTimerController.java` | HTTP 查询入口，获取当前房间计时器快照 |
| Service | `src/main/java/com/cqie/deepcover/game/service/GameTimerService.java` | 启动计时器、查询计时器、扫描到期计时器、发布到期事件 |
| Scheduler | `src/main/java/com/cqie/deepcover/game/scheduler/GameTimerScheduler.java` | 每秒调用一次 `expireDueTimers()`，统一扫描运行中的计时器 |
| 配置 | `src/main/java/com/cqie/deepcover/game/config/GameTimerConfig.java` | 开启 Spring 定时任务，并提供统一的 `Clock` |
| 房间开始监听器 | `src/main/java/com/cqie/deepcover/game/listener/RoomStartedTimerListener.java` | 监听 room 模块发布的 `RoomStartedEvent`，自动启动聊天阶段计时器 |
| Repository 接口 | `src/main/java/com/cqie/deepcover/game/interfaces/GameTimerRepository.java` | 计时器存储接口 |
| Repository 实现 | `src/main/java/com/cqie/deepcover/game/interfaces/impl/InMemoryGameTimerRepository.java` | 当前用内存 `ConcurrentHashMap` 临时代替 Redis 或数据库 |
| 事件发布接口 | `src/main/java/com/cqie/deepcover/game/interfaces/GameTimerEventPublisher.java` | 计时器事件发布抽象 |
| 事件发布实现 | `src/main/java/com/cqie/deepcover/game/interfaces/impl/SimpGameTimerEventPublisher.java` | 基于 `SimpMessagingTemplate` 向房间 WebSocket 订阅地址广播 |
| room 事件 | `src/main/java/com/cqie/deepcover/room/record/RoomStartedEvent.java` | room 模块在房间开始后发布的事件 |
| record 数据类 | `src/main/java/com/cqie/deepcover/game/record` | 计时器、计时器快照、广播事件等数据结构 |

## 当前内存存储结构

`InMemoryGameTimerRepository` 里有这行：

```java
private final Map<String, GameTimer> timers = new ConcurrentHashMap<>();
```

它相当于一张临时的内存计时器表：

```text
roomCode -> GameTimer
ABC123   -> GameTimer
QW8K2P   -> GameTimer
```

`ConcurrentHashMap` 用来保证多个请求线程、定时扫描线程同时读写时更安全。

注意：这仍然是内存存储，服务重启后计时器会丢失。后续如果要做得更完整，可以把 `GameTimerRepository` 换成 Redis 实现：

```text
GameTimerService -> GameTimerRepository -> RedisGameTimerRepository
```

这样 Service 层不用改。

## 核心设计

后端保存权威时间：

```text
startedAt
endsAt
durationSeconds
status
```

前端不负责决定计时是否结束，只负责显示。前端可以拿到 `endsAt` 后本地每秒刷新倒计时 UI；后端每秒扫描一次运行中的计时器，发现到期后标记为 `EXPIRED`，并广播 `TIMER_EXPIRED` 事件。

这个方案不是“每个房间一个线程”，而是：

```text
一个 Spring 定时任务 -> 每秒扫描所有 RUNNING timer -> 到期就处理
```

## 通用数据结构

### GameTimer

后端内部保存的计时器：

```json
{
  "roomCode": "ABC123",
  "phase": "CHATTING",
  "status": "RUNNING",
  "durationSeconds": 300,
  "startedAt": "2026-05-09T03:00:00Z",
  "endsAt": "2026-05-09T03:05:00Z"
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roomCode` | string | 房间号 |
| `phase` | string | 当前计时器对应的游戏阶段，当前主要是 `CHATTING` |
| `status` | string | 计时器状态：`RUNNING` 或 `EXPIRED` |
| `durationSeconds` | number | 总时长，单位秒 |
| `startedAt` | string | 开始时间 |
| `endsAt` | string | 结束时间 |

### GameTimerSnapshot

返回给前端展示的计时器快照：

```json
{
  "roomCode": "ABC123",
  "phase": "CHATTING",
  "status": "RUNNING",
  "durationSeconds": 300,
  "startedAt": "2026-05-09T03:00:00Z",
  "endsAt": "2026-05-09T03:05:00Z",
  "remainingSeconds": 180
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `remainingSeconds` | number | 后端根据当前时间和 `endsAt` 动态计算出的剩余秒数，最小为 0 |

`remainingSeconds` 不直接存储在 `GameTimer` 中，而是查询时实时计算：

```java
Duration.between(now, timer.endsAt()).toSeconds()
```

这样可以避免每秒更新一次数据库或 Map。

### GameRoomEvent

game 模块广播给前端的事件外壳：

```json
{
  "type": "TIMER_EXPIRED",
  "payload": {
    "roomCode": "ABC123",
    "phase": "CHATTING",
    "expiredAt": "2026-05-09T03:05:00Z"
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | string | 事件类型，当前 timer 模块会发 `TIMER_EXPIRED` |
| `payload` | object | 事件内容，当前是 `TimerExpiredPayload` |

广播地址和聊天模块一致：

```text
/topic/rooms/{roomCode}/events
```

这样前端只需要订阅一个房间事件通道，就能收到聊天、计时器、后续投票等事件。

## 接口 1：查询当前房间计时器

### 请求

```http
GET /api/rooms/{roomCode}/timer
X-Player-Token: token-xxx
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 当前房间号 |

Header 参数：

| Header | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Player-Token` | string | 是 | 当前玩家 token，用来确认请求者属于这个房间 |

请求体：无。

### 返回

HTTP 状态码：`200 OK`

```json
{
  "roomCode": "ABC123",
  "phase": "CHATTING",
  "status": "RUNNING",
  "durationSeconds": 300,
  "startedAt": "2026-05-09T03:00:00Z",
  "endsAt": "2026-05-09T03:05:00Z",
  "remainingSeconds": 180
}
```

### 代码流程

1. 前端请求 `GET /api/rooms/ABC123/timer`。
2. 请求 Header 带上 `X-Player-Token`。
3. 进入 `GameTimerController.snapshot(roomCode, playerToken)`。
4. Controller 先调用：

```java
roomService.requireChatParticipant(roomCode, playerToken);
```

5. room 模块检查房间是否存在。
6. room 模块检查房间状态是否是 `CHATTING`。
7. room 模块检查 token 是否属于这个房间里的玩家。
8. 如果校验失败，抛出对应的 `RoomException`。
9. 校验通过后，Controller 调用：

```java
gameTimerService.snapshot(roomCode);
```

10. `GameTimerService.snapshot()` 调用 `findTimer(roomCode)`。
11. `findTimer()` 调用：

```java
gameTimerRepository.findByRoomCode(roomCode)
```

12. 当前实际进入 `InMemoryGameTimerRepository.findByRoomCode(roomCode)`。
13. 底层从 `timers` Map 中执行：

```java
timers.get(roomCode)
```

14. 如果没有找到计时器，抛出 `TIMER_NOT_FOUND`。
15. 如果找到计时器，调用：

```java
GameTimerSnapshot.from(timer, clock.instant())
```

16. `GameTimerSnapshot.from()` 根据当前时间计算 `remainingSeconds`。
17. Controller 返回 `GameTimerSnapshot` 给前端。

## 自动启动计时器流程

当前没有单独暴露“启动计时器 HTTP 接口”。计时器由后端在房间开始后自动启动。

完整流程：

1. 房主请求：

```http
POST /api/rooms/{roomCode}/start
X-Player-Token: token-xxx
```

2. 进入 `RoomService.startRoom(roomCode, playerToken)`。
3. room 模块校验房主身份和玩家数量。
4. 调用：

```java
room.markChatting();
```

5. 调用：

```java
roomRepository.save(room);
```

6. 发布房间开始事件：

```java
eventPublisher.publishEvent(new RoomStartedEvent(roomCode));
```

7. `RoomStartedTimerListener` 监听到 `RoomStartedEvent`。
8. Listener 调用：

```java
gameTimerService.startTimer(event.roomCode(), GamePhase.CHATTING, Duration.ofSeconds(300));
```

9. `GameTimerService.startTimer()` 用当前时间生成：

```text
startedAt = now
endsAt = now + 300 seconds
status = RUNNING
phase = CHATTING
```

10. 调用 `gameTimerRepository.save(timer)` 保存计时器。
11. 当前实际进入 `InMemoryGameTimerRepository.save(timer)`：

```java
timers.put(timer.roomCode(), timer);
```

12. 内存结构变成：

```text
timers["ABC123"] = GameTimer(...)
```

## 后台扫描到期流程

`GameTimerScheduler` 中有：

```java
@Scheduled(fixedRate = 1000)
public void expireDueTimers() {
    gameTimerService.expireDueTimers();
}
```

意思是 Spring 每 1000 毫秒调用一次 `expireDueTimers()`。

流程：

1. Spring 定时任务触发 `GameTimerScheduler.expireDueTimers()`。
2. Scheduler 调用 `gameTimerService.expireDueTimers()`。
3. Service 获取当前时间：

```java
Instant now = clock.instant();
```

4. Service 调用：

```java
gameTimerRepository.findRunningTimers()
```

5. 当前实际进入 `InMemoryGameTimerRepository.findRunningTimers()`。
6. Repository 从 `timers.values()` 中筛选：

```java
timer.status() == TimerStatus.RUNNING
```

7. Service 遍历所有运行中的 timer。
8. 对每个 timer 调用：

```java
timer.dueAt(now)
```

9. 如果 `now >= endsAt`，说明计时器已经到期。
10. 调用：

```java
GameTimer expiredTimer = timer.expire();
```

11. `expire()` 会创建一个 `status = EXPIRED` 的新 `GameTimer`。
12. 调用 `gameTimerRepository.save(expiredTimer)` 保存新状态。
13. 调用 `publishExpiredEvent(expiredTimer, now)` 发布到期事件。
14. 当前实际进入 `SimpGameTimerEventPublisher.publish()`。
15. 发布地址：

```text
/topic/rooms/{roomCode}/events
```

16. 广播 JSON：

```json
{
  "type": "TIMER_EXPIRED",
  "payload": {
    "roomCode": "ABC123",
    "phase": "CHATTING",
    "expiredAt": "2026-05-09T03:05:00Z"
  }
}
```

17. 所有订阅这个房间事件地址的前端都会收到。

## 前端推荐使用方式

前端不要每秒请求后端。

推荐流程：

```text
进入房间或刷新页面
-> GET /api/rooms/{roomCode}/timer 拉一次权威快照
-> 根据 endsAt 在浏览器本地每秒刷新倒计时显示
-> 继续订阅 /topic/rooms/{roomCode}/events
-> 收到 TIMER_EXPIRED 后刷新页面状态或进入下一阶段 UI
```

这样后端压力小，前端显示也更顺滑。

## 错误情况

当前 timer 查询接口复用 room 模块的 `RoomExceptionHandler`。

| errorCode | HTTP 状态码 | 场景 |
| --- | --- | --- |
| `ROOM_NOT_FOUND` | 404 | 房间不存在 |
| `TIMER_NOT_FOUND` | 404 | 当前房间还没有计时器 |
| `ROOM_NOT_CHATTING` | 400 | 房间还没进入聊天阶段 |
| `PLAYER_NOT_FOUND` | 400 | `X-Player-Token` 找不到对应玩家 |

## 当前接口和事件汇总

| 类型 | 地址 | 参数 | 返回/广播 | 主要作用 |
| --- | --- | --- | --- | --- |
| HTTP GET | `/api/rooms/{roomCode}/timer` | path: `roomCode`，header: `X-Player-Token` | `GameTimerSnapshot` | 查询当前房间计时器 |
| WebSocket Event | `/topic/rooms/{roomCode}/events` | 订阅地址 | `TIMER_EXPIRED` | 计时器到期通知 |

## 后续扩展方向

| 后续功能 | 可以怎么接 |
| --- | --- |
| 聊天到期进入投票 | 在收到 `TIMER_EXPIRED(CHATTING)` 后，由 game/vote 模块把房间状态改成 `VOTING` |
| 投票阶段计时 | 复用 `gameTimerService.startTimer(roomCode, GamePhase.VOTING, duration)` |
| Redis 存储 | 新增 `RedisGameTimerRepository` 替换内存实现 |
| 前端实时 UI | 查询一次 `endsAt`，本地倒计时；收到 `TIMER_EXPIRED` 后切换 UI |
| 多阶段事件 | 在 `GameEventType` 里继续增加 `VOTE_STARTED`、`GAME_ENDED` 等事件 |
