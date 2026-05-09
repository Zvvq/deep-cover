# Chat 模块接口与流程说明

这份文档按当前代码整理，用来说明 chat 模块提供了哪些 WebSocket/STOMP 接口、每个接口需要什么参数、会广播什么数据，以及一条聊天消息从前端发出后，在 Controller、Service、Repository、WebSocket 广播之间如何流转。

## 代码位置

| 类型 | 路径 | 作用 |
| --- | --- | --- |
| WebSocket 配置 | `src/main/java/com/cqie/deepcover/chat/config/WebSocketConfig.java` | 注册 WebSocket 入口、配置发送前缀和广播前缀 |
| Controller | `src/main/java/com/cqie/deepcover/chat/controller/ChatController.java` | WebSocket 消息入口，接收前端发送的聊天消息 |
| 历史消息 Controller | `src/main/java/com/cqie/deepcover/chat/controller/ChatHistoryController.java` | HTTP 查询入口，获取当前房间的全部历史聊天消息 |
| Service | `src/main/java/com/cqie/deepcover/chat/service/ChatService.java` | 聊天业务规则，校验、保存、广播都在这里处理 |
| Repository 接口 | `src/main/java/com/cqie/deepcover/chat/interfaces/ChatMessageRepository.java` | 聊天消息存储接口 |
| Repository 实现 | `src/main/java/com/cqie/deepcover/chat/interfaces/impl/InMemoryChatMessageRepository.java` | 当前用内存 `ConcurrentHashMap` 临时代替数据库 |
| 事件发布接口 | `src/main/java/com/cqie/deepcover/chat/interfaces/ChatEventPublisher.java` | 聊天事件发布抽象 |
| 事件发布实现 | `src/main/java/com/cqie/deepcover/chat/interfaces/impl/SimpChatEventPublisher.java` | 基于 `SimpMessagingTemplate` 把事件发给 WebSocket 订阅者 |
| record 数据类 | `src/main/java/com/cqie/deepcover/chat/record` | 聊天请求、聊天消息、广播事件等数据结构 |
| 事件枚举 | `src/main/java/com/cqie/deepcover/chat/enums/RoomEventType.java` | 房间事件类型，当前只有 `CHAT_MESSAGE` |

## 当前内存存储结构

`InMemoryChatMessageRepository` 里有这行：

```java
private final Map<String, CopyOnWriteArrayList<ChatMessage>> messages = new ConcurrentHashMap<>();
```

它相当于一张临时的内存聊天记录表：

```text
roomCode -> 聊天消息列表
ABC123   -> [ChatMessage, ChatMessage, ChatMessage]
QW8K2P   -> [ChatMessage, ChatMessage]
```

这里用了两个并发集合：

| 类型 | 作用 |
| --- | --- |
| `ConcurrentHashMap` | 让多个线程同时按房间号读写消息列表时更安全 |
| `CopyOnWriteArrayList` | 让同一个房间的消息列表在并发追加时更安全 |

注意：这仍然是内存存储，服务重启后聊天记录会丢失。后续如果换数据库或 Redis，只需要新增 `ChatMessageRepository` 的实现类，再替换 Spring Bean。

## WebSocket/STOMP 基础配置

当前配置在 `WebSocketConfig`：

```java
registry.enableSimpleBroker("/topic");
registry.setApplicationDestinationPrefixes("/app");
registry.addEndpoint("/ws");
```

含义如下：

| 配置 | 说明 |
| --- | --- |
| `/ws` | 浏览器建立 WebSocket 连接的入口 |
| `/app` | 前端发送消息到后端 Controller 时使用的前缀 |
| `/topic` | 后端广播消息给前端时使用的前缀 |

所以实际使用时是：

```text
连接地址：/ws
发送聊天：/app/rooms/{roomCode}/chat
订阅事件：/topic/rooms/{roomCode}/events
```

## 通用数据结构

### ChatMessageRequest

前端发送聊天消息时传入：

```json
{
  "playerToken": "token-xxx",
  "content": "大家先聊聊今天的话题"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `playerToken` | string | 是 | 当前玩家 token，用来判断是谁发言 |
| `content` | string | 是 | 聊天内容，后端会去掉前后空格，最大长度 300 |

这里使用 `playerToken`，不是 `playerId`，原因是 `playerId` 会出现在房间快照里，其他玩家也能看到；`playerToken` 只保存在当前玩家本地，更适合当身份凭证。

### ChatMessage

后端内部保存的聊天消息：

```json
{
  "id": "message-uuid",
  "roomCode": "ABC123",
  "senderPlayerId": "player-xxx",
  "content": "大家先聊聊今天的话题",
  "createdAt": "2026-05-09T03:15:00Z"
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 消息 ID，由后端生成，格式类似 `message-{uuid}` |
| `roomCode` | string | 房间号 |
| `senderPlayerId` | string | 发送者玩家 ID |
| `content` | string | 清理后的聊天内容 |
| `createdAt` | string | 消息创建时间 |

### ChatMessageResponse

广播给前端的聊天消息：

```json
{
  "id": "message-uuid",
  "roomCode": "ABC123",
  "senderPlayerId": "player-xxx",
  "content": "大家先聊聊今天的话题",
  "createdAt": "2026-05-09T03:15:00Z"
}
```

它和 `ChatMessage` 当前字段一样，但语义不同：

| 类型 | 用途 |
| --- | --- |
| `ChatMessage` | 后端内部保存 |
| `ChatMessageResponse` | 对外广播给前端 |

这样分开写，是为了后续扩展时更稳。比如以后 `ChatMessage` 里可能保存敏感字段、AI 判断过程、审核状态，但这些不一定都要广播给前端。

### RoomEvent

后端广播给前端的统一事件外壳：

```json
{
  "type": "CHAT_MESSAGE",
  "payload": {
    "id": "message-uuid",
    "roomCode": "ABC123",
    "senderPlayerId": "player-xxx",
    "content": "大家先聊聊今天的话题",
    "createdAt": "2026-05-09T03:15:00Z"
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | string | 事件类型，当前是 `CHAT_MESSAGE` |
| `payload` | object | 事件内容，当前是 `ChatMessageResponse` |

为什么要用 `RoomEvent` 包一层？

- 前端可以先看 `type`，再决定怎么解析 `payload`。
- 后续投票、AI 发言、游戏结束都能复用同一个订阅地址。
- 不需要为每种事件都新建一个 WebSocket 订阅通道。

## 接口 1：连接 WebSocket

### 请求

```text
WebSocket /ws
```

浏览器连接示例：

```js
const socket = new WebSocket("ws://localhost:8080/ws");
```

### STOMP CONNECT 帧

WebSocket 连接打开后，需要发送 STOMP 的 `CONNECT` 帧：

```text
CONNECT
accept-version:1.2
heart-beat:0,0

\0
```

### 流程

1. 前端创建 `new WebSocket("/ws")`。
2. 进入后端 `WebSocketConfig.registerStompEndpoints()` 注册的 `/ws` 入口。
3. WebSocket 连接成功后，前端发送 STOMP `CONNECT` 帧。
4. Spring WebSocket 收到后建立 STOMP 会话。
5. 连接成功后，后端会返回 `CONNECTED` 帧。
6. 前端收到 `CONNECTED` 后，就可以订阅房间事件。

## 接口 2：订阅房间事件

### 请求

```text
SUBSCRIBE /topic/rooms/{roomCode}/events
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 要订阅的房间号 |

STOMP 帧示例：

```text
SUBSCRIBE
id:room-events-ABC123
destination:/topic/rooms/ABC123/events

\0
```

### 接收数据

后端广播聊天消息时，订阅者会收到：

```json
{
  "type": "CHAT_MESSAGE",
  "payload": {
    "id": "message-uuid",
    "roomCode": "ABC123",
    "senderPlayerId": "player-xxx",
    "content": "大家先聊聊今天的话题",
    "createdAt": "2026-05-09T03:15:00Z"
  }
}
```

### 流程

1. 前端确认当前房间状态是 `CHATTING`。
2. 前端发送 `SUBSCRIBE` 帧，订阅 `/topic/rooms/ABC123/events`。
3. Spring 的 simple broker 记录这个订阅关系。
4. 后续只要后端向 `/topic/rooms/ABC123/events` 发送事件，所有订阅这个地址的客户端都会收到。

## 接口 3：发送聊天消息

### 请求

```text
SEND /app/rooms/{roomCode}/chat
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 当前房间号 |

消息体：

```json
{
  "playerToken": "token-xxx",
  "content": "大家先聊聊今天的话题"
}
```

完整 STOMP 帧示例：

```text
SEND
destination:/app/rooms/ABC123/chat
content-type:application/json

{"playerToken":"token-xxx","content":"大家先聊聊今天的话题"}\0
```

### 直接返回值

当前 `ChatController.sendMessage()` 返回 `void`，所以发送消息本身没有普通 HTTP 那种直接返回值。

消息发送成功后，后端会通过订阅地址广播：

```text
/topic/rooms/{roomCode}/events
```

前端应该从订阅事件里拿到最终消息。

### 广播数据

```json
{
  "type": "CHAT_MESSAGE",
  "payload": {
    "id": "message-uuid",
    "roomCode": "ABC123",
    "senderPlayerId": "player-xxx",
    "content": "大家先聊聊今天的话题",
    "createdAt": "2026-05-09T03:15:00Z"
  }
}
```

## 发送聊天消息的代码流程

下面按一条消息从前端发送开始，完整走一遍。

1. 前端已经通过 `/ws` 建立 WebSocket 连接。
2. 前端已经订阅 `/topic/rooms/ABC123/events`。
3. 玩家在页面输入聊天内容。
4. 前端发送 STOMP `SEND` 帧到：

```text
/app/rooms/ABC123/chat
```

5. 因为 `WebSocketConfig` 里配置了：

```java
registry.setApplicationDestinationPrefixes("/app");
```

所以 Spring 会把 `/app` 去掉，用剩下的 `/rooms/ABC123/chat` 去匹配 Controller。

6. 进入 `ChatController.sendMessage()`：

```java
@MessageMapping("/rooms/{roomCode}/chat")
public void sendMessage(
        @DestinationVariable String roomCode,
        @Payload ChatMessageRequest request
) {
    chatService.sendMessage(roomCode, request);
}
```

7. `@DestinationVariable String roomCode` 从路径中拿到 `ABC123`。
8. `@Payload ChatMessageRequest request` 从 JSON 消息体中解析出：

```json
{
  "playerToken": "token-xxx",
  "content": "大家先聊聊今天的话题"
}
```

9. Controller 调用 `chatService.sendMessage(roomCode, request)`。
10. 进入 `ChatService.sendMessage()`。
11. 先调用 `normalizeContent(request)` 清理聊天内容。
12. `normalizeContent()` 会做三件事：
    - 如果 `request` 或 `content` 是 `null`，抛出 `INVALID_CHAT_MESSAGE`
    - 如果 `content.trim()` 后是空字符串，抛出 `INVALID_CHAT_MESSAGE`
    - 如果内容长度超过 300，抛出 `INVALID_CHAT_MESSAGE`
13. 如果内容合法，得到去掉前后空格后的 `content`。
14. 调用 `roomService.requireChatParticipant(roomCode, request.playerToken())`。
15. 进入 room 模块的 `RoomService.requireChatParticipant()`。
16. 先调用 `findRoom(roomCode)` 查找房间。
17. `findRoom()` 调用 `roomRepository.findByCode(roomCode)`。
18. 当前实际进入 `InMemoryRoomRepository.findByCode(roomCode)`。
19. 底层从 room 模块的 `rooms` 里执行：

```java
rooms.get(roomCode)
```

20. 如果房间不存在，抛出 `ROOM_NOT_FOUND`。
21. 如果房间存在，检查：

```java
room.status() != RoomStatus.CHATTING
```

22. 如果房间还不是 `CHATTING`，说明游戏还没开始，抛出 `ROOM_NOT_CHATTING`。
23. 如果房间已经是 `CHATTING`，继续根据 token 找玩家。
24. 调用 `findPlayer(room, playerToken)`。
25. `findPlayer()` 调用 `room.findPlayerByToken(playerToken)`。
26. `Room.findPlayerByToken()` 遍历当前房间的 `players` 列表，找到 token 匹配的玩家。
27. 如果找不到玩家，抛出 `PLAYER_NOT_FOUND`。
28. 如果找到玩家，返回 `Player sender`。
29. 回到 `ChatService.sendMessage()`。
30. 创建 `ChatMessage`：

```java
ChatMessage message = new ChatMessage(
        nextMessageId(),
        roomCode,
        sender.id(),
        content,
        Instant.now()
);
```

31. `nextMessageId()` 内部使用 `UUID.randomUUID()` 生成消息 ID，格式类似：

```text
message-3fa85f64-5717-4562-b3fc-2c963f66afa6
```

32. 调用 `chatMessageRepository.save(message)` 保存消息。
33. 当前实际进入 `InMemoryChatMessageRepository.save(message)`。
34. `save()` 执行：

```java
messages.computeIfAbsent(message.roomCode(), roomCode -> new CopyOnWriteArrayList<>())
        .add(message);
```

35. 这行代码的意思是：
    - 先根据 `roomCode` 从 `messages` 里取消息列表
    - 如果这个房间还没有消息列表，就创建一个新的 `CopyOnWriteArrayList`
    - 然后把当前 `message` 追加到这个列表里
36. 保存后的结构类似：

```text
messages["ABC123"] = [
  ChatMessage(id="message-1", content="第一句"),
  ChatMessage(id="message-2", content="第二句")
]
```

37. 保存完成后，调用 `ChatMessageResponse.from(message)`，把内部消息转换成对外广播用的响应对象。
38. 创建房间事件：

```java
new RoomEvent(RoomEventType.CHAT_MESSAGE, response)
```

39. 调用 `chatEventPublisher.publish(roomCode, event)` 发布事件。
40. 当前实际进入 `SimpChatEventPublisher.publish(roomCode, event)`。
41. `publish()` 执行：

```java
messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/events", event);
```

42. 如果 `roomCode = "ABC123"`，实际广播地址就是：

```text
/topic/rooms/ABC123/events
```

43. Spring WebSocket 的 simple broker 找到所有订阅这个地址的客户端。
44. 后端把 `RoomEvent` 转成 JSON，发送给这些客户端。
45. 前端收到事件后，根据 `type = "CHAT_MESSAGE"`，把 `payload` 渲染到聊天列表。

## 接口 4：查询当前房间全部历史消息

### 请求

```http
GET /api/rooms/{roomCode}/messages
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

返回当前房间的全部消息数组：

```json
[
  {
    "id": "message-uuid-1",
    "roomCode": "ABC123",
    "senderPlayerId": "player-xxx",
    "content": "第一句",
    "createdAt": "2026-05-09T03:15:00Z"
  },
  {
    "id": "message-uuid-2",
    "roomCode": "ABC123",
    "senderPlayerId": "player-yyy",
    "content": "第二句",
    "createdAt": "2026-05-09T03:16:00Z"
  }
]
```

如果当前房间还没有消息，返回空数组：

```json
[]
```

### 代码流程

1. 前端请求 `GET /api/rooms/ABC123/messages`。
2. 请求 Header 带上 `X-Player-Token`。
3. 进入 `ChatHistoryController.findMessages(roomCode, playerToken)`。
4. Controller 从路径中拿到 `roomCode = "ABC123"`。
5. Controller 从 Header 中拿到当前玩家的 `playerToken`。
6. Controller 调用 `chatService.findMessages(roomCode, playerToken)`。
7. 进入 `ChatService.findMessages()`。
8. 先调用 `roomService.requireChatParticipant(roomCode, playerToken)`。
9. room 模块会检查房间是否存在。
10. room 模块会检查房间状态是否是 `CHATTING`。
11. room 模块会检查 `playerToken` 是否能在这个房间里找到玩家。
12. 如果任一校验失败，会抛出对应的 `RoomException`。
13. 校验通过后，调用：

```java
chatMessageRepository.findByRoomCode(roomCode)
```

14. 当前实际进入 `InMemoryChatMessageRepository.findByRoomCode(roomCode)`。
15. 底层从 chat 模块的 `messages` 里取数据：

```java
messages.getOrDefault(roomCode, new CopyOnWriteArrayList<>())
```

16. 如果这个房间有消息，就返回这个房间的消息列表副本。
17. 如果这个房间还没有消息，就返回空列表。
18. `ChatService.findMessages()` 把每个 `ChatMessage` 转成 `ChatMessageResponse`。
19. Controller 把 `List<ChatMessageResponse>` 返回给前端。

### 为什么历史消息用 HTTP，不用 WebSocket？

实时新增消息适合 WebSocket，因为后端需要主动推送给所有玩家。

历史消息查询更适合 HTTP，因为它是一次性的“拉取已有数据”：

- 刷新页面后先调用这个接口恢复聊天记录。
- 然后再连接 WebSocket，继续接收新消息。
- 后续做分页时，也可以自然扩展成 `?page=1&pageSize=50`。

## 错误情况

当前聊天模块复用了 room 模块的 `RoomException` 和 `RoomErrorCode`。

| errorCode | 场景 |
| --- | --- |
| `ROOM_NOT_FOUND` | 房间号不存在 |
| `ROOM_NOT_CHATTING` | 房间还没进入 `CHATTING`，也就是游戏还没开始 |
| `PLAYER_NOT_FOUND` | `playerToken` 找不到对应玩家 |
| `INVALID_CHAT_MESSAGE` | 聊天内容为空、全是空格，或者长度超过 300 |

注意：这些错误如果发生在普通 HTTP 接口里，会由 `RoomExceptionHandler` 转成 JSON HTTP 响应。但发送聊天消息走的是 WebSocket/STOMP，不是普通 HTTP，所以前端不能按 HTTP 响应去接收错误。

当前版本里，聊天失败更适合先在前端做基础校验，比如：

- 房间状态不是 `CHATTING` 时禁用输入框。
- 输入内容为空时不发送。
- 输入框 `maxlength="300"`。

后续如果要做更完整的 WebSocket 错误提示，可以增加专门的 STOMP 错误处理，把错误也包装成 `RoomEvent` 广播或单独发给当前玩家。

## 当前接口汇总

| 类型 | 地址 | 参数 | 返回/广播 | 主要作用 |
| --- | --- | --- | --- | --- |
| WebSocket CONNECT | `/ws` | 无 | `CONNECTED` 帧 | 建立 WebSocket/STOMP 连接 |
| STOMP SUBSCRIBE | `/topic/rooms/{roomCode}/events` | path: `roomCode` | 接收 `RoomEvent` | 订阅房间事件 |
| STOMP SEND | `/app/rooms/{roomCode}/chat` | path: `roomCode`，body: `ChatMessageRequest` | 广播 `CHAT_MESSAGE` 事件 | 发送聊天消息 |
| HTTP GET | `/api/rooms/{roomCode}/messages` | path: `roomCode`，header: `X-Player-Token` | `List<ChatMessageResponse>` | 查询当前房间全部历史消息 |

## 和 room 模块的关系

chat 模块不会自己直接查 room 模块的 `InMemoryRoomRepository`，而是调用：

```java
roomService.requireChatParticipant(roomCode, playerToken)
```

这样做的好处是：

- 房间是否存在，由 room 模块判断。
- 房间是否已经开始，由 room 模块判断。
- token 对应哪个玩家，由 room 模块判断。
- chat 模块只负责聊天本身，不重复实现房间规则。

简单说，room 模块管“这个人有没有资格在这个房间发言”，chat 模块管“这句话怎么保存、怎么广播”。

## 后续扩展方向

当前结构已经给后续玩法留了扩展空间：

| 后续功能 | 可以怎么接 |
| --- | --- |
| AI Agent 发言 | 让 Java 调 Python Agent 后，拿到 AI 发言内容，再复用 chat 模块保存和广播 |
| AI 选择不发言 | Python 返回“不发言”标记，Java 不调用聊天保存和广播 |
| 投票开始 | 在 `RoomEventType` 增加 `VOTE_STARTED` |
| 投票结果 | 在 `RoomEventType` 增加 `VOTE_RESULT` |
| 系统消息 | 在 `RoomEventType` 增加 `SYSTEM_MESSAGE`，或者新增系统消息 record |
| 聊天记录落库 | 新增数据库版 `ChatMessageRepository` 实现 |
