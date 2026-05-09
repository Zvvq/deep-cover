# Room 模块接口与流程说明

这份文档按当前代码整理，用来说明 room 模块提供了哪些 HTTP 接口、每个接口需要什么参数、返回什么数据，以及请求进入后在 Controller、Service、Repository、内存 Map 之间怎么流转。

## 代码位置

| 类型 | 路径 | 作用 |
| --- | --- | --- |
| Controller | `src/main/java/com/cqie/deepcover/room/controller/RoomController.java` | HTTP 接口入口，只负责接参数、调 Service、返回响应 |
| Service | `src/main/java/com/cqie/deepcover/room/service/RoomService.java` | 房间业务规则，创建、加入、开始、离开都在这里处理 |
| Repository 接口 | `src/main/java/com/cqie/deepcover/room/interfaces/RoomRepository.java` | 房间存储接口，Service 依赖这个接口 |
| Repository 实现 | `src/main/java/com/cqie/deepcover/room/interfaces/impl/InMemoryRoomRepository.java` | 当前用内存 `ConcurrentHashMap` 临时充当数据库 |
| 房间模型 | `src/main/java/com/cqie/deepcover/room/model/Room.java` | 房间内部状态和玩家列表 |
| record 数据类 | `src/main/java/com/cqie/deepcover/room/record` | 接口响应、业务返回值、快照数据 |

## 当前内存存储结构

`InMemoryRoomRepository` 里有这行：

```java
private final Map<String, Room> rooms = new ConcurrentHashMap<>();
```

它相当于一张临时的内存房间表：

```text
roomCode -> Room
ABC123   -> Room 对象
QW8K2P   -> Room 对象
```

`ConcurrentHashMap` 保证多个请求线程同时读写 Map 时更安全。`InMemoryRoomRepository` 被 `@Repository` 注册成 Spring Bean，Spring 默认 Bean 是单例，所以正常单进程运行时，所有请求共用同一个 `rooms`。

注意：这是内存存储，服务重启后数据会丢失。后续如果换成数据库或 Redis，只需要替换 `RoomRepository` 的实现。

## 通用数据结构

### RoomSnapshot

大部分接口都会返回房间快照：

```json
{
  "roomCode": "ABC123",
  "status": "WAITING",
  "hostPlayerId": "player-xxx",
  "players": [
    {
      "id": "player-xxx",
      "type": "HUMAN",
      "alive": true,
      "host": true
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roomCode` | string | 房间号 |
| `status` | string | 房间状态：`WAITING`、`CHATTING`、`VOTING`、`ENDED`、`DESTROYED` |
| `hostPlayerId` | string | 房主玩家 ID |
| `players` | array | 当前房间玩家快照 |

### PlayerSnapshot

```json
{
  "id": "player-xxx",
  "type": "HUMAN",
  "alive": true,
  "host": true
}
```

这里不会返回 `token`。`token` 是玩家身份凭证，只给当前浏览器自己使用，不能暴露给其他玩家。

### 错误响应

业务异常由 `RoomExceptionHandler` 转成统一错误响应：

```json
{
  "errorCode": "ROOM_NOT_FOUND",
  "message": "Room not found."
}
```

错误码和 HTTP 状态码映射：

| errorCode | HTTP 状态码 | 场景 |
| --- | --- | --- |
| `ROOM_NOT_FOUND` | 404 | 房间不存在 |
| `FORBIDDEN` | 403 | 非房主执行房主操作 |
| `ROOM_NOT_JOINABLE` | 400 | 房间已经开始或不能加入 |
| `ROOM_FULL` | 400 | 房间真人数量达到 8 人 |
| `NOT_ENOUGH_PLAYERS` | 400 | 少于 2 名真人时开始游戏 |
| `PLAYER_NOT_FOUND` | 400 | token 找不到对应玩家 |

## 接口 1：创建房间

### 请求

```http
POST /api/rooms
```

请求参数：无。

请求体：无。

### 返回

HTTP 状态码：`201 Created`

```json
{
  "roomCode": "ABC123",
  "playerId": "player-xxx",
  "playerToken": "token-xxx",
  "snapshot": {
    "roomCode": "ABC123",
    "status": "WAITING",
    "hostPlayerId": "player-xxx",
    "players": [
      {
        "id": "player-xxx",
        "type": "HUMAN",
        "alive": true,
        "host": true
      }
    ]
  }
}
```

### 代码流程

1. 前端请求 `POST /api/rooms`。
2. 进入 `RoomController.createRoom()`。
3. Controller 调用 `roomService.createRoom()`。
4. `RoomService.createRoom()` 先调用 `roomRepository.nextRoomCode()` 生成房间号。
5. 当前实现类是 `InMemoryRoomRepository`，所以实际进入 `InMemoryRoomRepository.nextRoomCode()`。
6. `nextRoomCode()` 内部调用 `randomRoomCode()` 随机生成 6 位房间号。
7. `randomRoomCode()` 从 `ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789` 里随机取 6 个字符。
8. `nextRoomCode()` 用 `rooms.containsKey(roomCode)` 检查这个房间号是否已经存在。
9. 如果已经存在，就重新生成；如果不存在，就返回这个房间号。
10. `RoomService.createRoom()` 调用 `nextId()` 生成房主玩家 ID，例如 `player-uuid`。
11. `RoomService.createRoom()` 调用 `nextToken()` 生成房主玩家 token。
12. 调用 `Player.humanHost(nextId(), nextToken())` 创建房主玩家。
13. 调用 `Room.createWaiting(roomCode, host)` 创建等待中的房间。
14. `Room` 构造时会设置：
    - `roomCode`
    - `hostPlayerId`
    - `players` 列表，并把房主放进去
    - `status = WAITING`
15. 调用 `roomRepository.save(room)` 保存房间。
16. 当前实际进入 `InMemoryRoomRepository.save(room)`。
17. `save(room)` 执行：

```java
rooms.put(room.roomCode(), room);
```

18. 这一步把房间放入内存 Map，结构类似：

```text
rooms["ABC123"] = Room对象
```

19. `RoomService.createRoom()` 调用私有方法 `snapshot(room)`，把内部 `Room` 转成 `RoomSnapshot`。
20. 返回 `RoomCreateResult`。
21. Controller 调用 `RoomDtos.CreateRoomResponse.from(result)`，把业务结果转换成 HTTP 响应。
22. 返回给前端。

## 接口 2：加入房间

### 请求

```http
POST /api/rooms/{roomCode}/join
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 要加入的房间号 |

请求体：无。

### 返回

HTTP 状态码：`200 OK`

```json
{
  "roomCode": "ABC123",
  "playerId": "player-yyy",
  "playerToken": "token-yyy",
  "snapshot": {
    "roomCode": "ABC123",
    "status": "WAITING",
    "hostPlayerId": "player-xxx",
    "players": [
      {
        "id": "player-xxx",
        "type": "HUMAN",
        "alive": true,
        "host": true
      },
      {
        "id": "player-yyy",
        "type": "HUMAN",
        "alive": true,
        "host": false
      }
    ]
  }
}
```

### 代码流程

1. 前端请求 `POST /api/rooms/ABC123/join`。
2. 进入 `RoomController.joinRoom(@PathVariable String roomCode)`。
3. Controller 从路径里拿到 `roomCode = "ABC123"`。
4. Controller 调用 `roomService.joinRoom(roomCode)`。
5. `RoomService.joinRoom()` 调用 `findRoom(roomCode)`。
6. `findRoom()` 调用 `roomRepository.findByCode(roomCode)`。
7. 当前实际进入 `InMemoryRoomRepository.findByCode(roomCode)`。
8. `findByCode()` 执行：

```java
Optional.ofNullable(rooms.get(roomCode));
```

9. 也就是从 `ConcurrentHashMap` 里取：

```text
rooms.get("ABC123")
```

10. 如果房间不存在，抛出 `RoomException(ROOM_NOT_FOUND)`。
11. 如果房间存在，检查 `room.status()` 是否为 `WAITING`。
12. 如果不是 `WAITING`，说明房间已开始或不可加入，抛出 `ROOM_NOT_JOINABLE`。
13. 检查 `room.humanPlayerCount()` 是否已经达到 8。
14. 如果已经 8 人，抛出 `ROOM_FULL`。
15. 调用 `Player.human(nextId(), nextToken())` 创建普通真人玩家。
16. 调用 `room.addPlayer(player)` 把玩家加入房间内部 `players` 列表。
17. 调用 `roomRepository.save(room)` 重新保存房间。
18. 当前实现是 `rooms.put(room.roomCode(), room)`，会用同一个房间号覆盖旧 Room 对象引用。
19. 调用 `snapshot(room)` 生成最新房间快照。
20. 返回 `RoomJoinResult`。
21. Controller 调用 `RoomDtos.JoinRoomResponse.from(result)` 转成响应。
22. 返回给前端。

## 接口 3：获取房间快照

### 请求

```http
GET /api/rooms/{roomCode}/snapshot
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 要查询的房间号 |

请求体：无。

### 返回

HTTP 状态码：`200 OK`

```json
{
  "roomCode": "ABC123",
  "status": "WAITING",
  "hostPlayerId": "player-xxx",
  "players": [
    {
      "id": "player-xxx",
      "type": "HUMAN",
      "alive": true,
      "host": true
    }
  ]
}
```

### 代码流程

1. 前端请求 `GET /api/rooms/ABC123/snapshot`。
2. 进入 `RoomController.snapshot(roomCode)`。
3. Controller 调用 `roomService.snapshot(roomCode)`。
4. `RoomService.snapshot(roomCode)` 调用 `findRoom(roomCode)`。
5. `findRoom()` 调用 `roomRepository.findByCode(roomCode)`。
6. 当前实现从 `rooms` 里执行 `rooms.get(roomCode)`。
7. 如果没找到，抛出 `ROOM_NOT_FOUND`。
8. 如果找到，调用私有方法 `snapshot(room)`。
9. `snapshot(room)` 将内部 `Room` 转成对外的 `RoomSnapshot`。
10. 返回快照给前端。

这个接口只读数据，不会修改 `rooms`。

## 接口 4：开始房间

### 请求

```http
POST /api/rooms/{roomCode}/start
X-Player-Token: token-xxx
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 要开始的房间号 |

Header 参数：

| Header | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Player-Token` | string | 是 | 当前玩家 token，用来判断是不是房主 |

请求体：无。

### 返回

HTTP 状态码：`200 OK`

```json
{
  "roomCode": "ABC123",
  "status": "CHATTING",
  "hostPlayerId": "player-xxx",
  "players": [
    {
      "id": "player-xxx",
      "type": "HUMAN",
      "alive": true,
      "host": true
    },
    {
      "id": "player-yyy",
      "type": "HUMAN",
      "alive": true,
      "host": false
    }
  ]
}
```

### 代码流程

1. 前端请求 `POST /api/rooms/ABC123/start`。
2. 请求 Header 带上 `X-Player-Token`。
3. 进入 `RoomController.startRoom(roomCode, playerToken)`。
4. Controller 调用 `roomService.startRoom(roomCode, playerToken)`。
5. `RoomService.startRoom()` 调用 `findRoom(roomCode)`。
6. `findRoom()` 从 `rooms` 里查房间。
7. 如果房间不存在，抛出 `ROOM_NOT_FOUND`。
8. 调用 `findPlayer(room, playerToken)`。
9. `findPlayer()` 调用 `room.findPlayerByToken(playerToken)`。
10. `Room.findPlayerByToken()` 会遍历房间的 `players` 列表，找到 token 匹配的玩家。
11. 如果没找到，抛出 `PLAYER_NOT_FOUND`。
12. 检查 `requester.host()`。
13. 如果当前 token 对应玩家不是房主，抛出 `FORBIDDEN`。
14. 检查 `room.humanPlayerCount()` 是否小于 2。
15. 如果真人少于 2 人，抛出 `NOT_ENOUGH_PLAYERS`。
16. 调用 `room.markChatting()`，把房间状态从 `WAITING` 改成 `CHATTING`。
17. 调用 `roomRepository.save(room)` 保存更新后的房间。
18. 当前实际执行 `rooms.put(room.roomCode(), room)`。
19. 调用 `snapshot(room)` 返回最新房间快照。

## 接口 5：离开房间

### 请求

```http
POST /api/rooms/{roomCode}/leave
X-Player-Token: token-xxx
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roomCode` | string | 是 | 要离开的房间号 |

Header 参数：

| Header | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Player-Token` | string | 是 | 当前玩家 token |

请求体：无。

### 返回：普通玩家离开

HTTP 状态码：`200 OK`

```json
{
  "roomCode": "ABC123",
  "status": "WAITING",
  "hostPlayerId": "player-xxx",
  "players": [
    {
      "id": "player-xxx",
      "type": "HUMAN",
      "alive": true,
      "host": true
    }
  ]
}
```

### 返回：房主离开

HTTP 状态码：`200 OK`

```json
{
  "roomCode": "ABC123",
  "status": "DESTROYED",
  "hostPlayerId": "player-xxx",
  "players": [
    {
      "id": "player-xxx",
      "type": "HUMAN",
      "alive": true,
      "host": true
    }
  ]
}
```

注意：房主离开时，接口会先返回 `DESTROYED` 快照，然后从内存仓库删除房间。后续再查询这个房间会返回 `ROOM_NOT_FOUND`。

### 代码流程：普通玩家离开

1. 前端请求 `POST /api/rooms/ABC123/leave`。
2. 请求 Header 带上 `X-Player-Token`。
3. 进入 `RoomController.leaveRoom(roomCode, playerToken)`。
4. Controller 调用 `roomService.leaveRoom(roomCode, playerToken)`。
5. `RoomService.leaveRoom()` 调用 `findRoom(roomCode)`。
6. 从 `rooms` 里找到房间。
7. 调用 `findPlayer(room, playerToken)` 找到当前玩家。
8. 检查 `player.host()`。
9. 如果不是房主，调用 `room.removePlayer(player.id())`。
10. `Room.removePlayer()` 会从内部 `players` 列表里删除该玩家。
11. 调用 `roomRepository.save(room)` 保存更新。
12. 当前实际执行 `rooms.put(room.roomCode(), room)`。
13. 返回最新 `RoomSnapshot`。

### 代码流程：房主离开

1. 前端请求 `POST /api/rooms/ABC123/leave`。
2. 请求 Header 带上房主的 `X-Player-Token`。
3. 进入 `RoomController.leaveRoom(roomCode, playerToken)`。
4. Controller 调用 `roomService.leaveRoom(roomCode, playerToken)`。
5. `RoomService.leaveRoom()` 通过 `findRoom(roomCode)` 找到房间。
6. 通过 `findPlayer(room, playerToken)` 找到玩家。
7. 检查 `player.host()`，发现是房主。
8. 调用 `room.markDestroyed()`，把房间状态改成 `DESTROYED`。
9. 调用 `snapshot(room)` 生成销毁状态的快照。
10. 调用 `roomRepository.deleteByCode(roomCode)` 删除房间。
11. 当前实际进入 `InMemoryRoomRepository.deleteByCode(roomCode)`。
12. `deleteByCode()` 执行：

```java
rooms.remove(roomCode);
```

13. 这一步会从 `ConcurrentHashMap` 里移除：

```text
rooms.remove("ABC123")
```

14. 返回刚才生成的 `DESTROYED` 快照。

## Repository 方法说明

### save(Room room)

```java
rooms.put(room.roomCode(), room);
```

作用：保存或覆盖房间。

如果房间号不存在，就是新增：

```text
ABC123 -> Room
```

如果房间号已存在，就是更新同一个房间号对应的 Room。

### findByCode(String roomCode)

```java
Optional.ofNullable(rooms.get(roomCode));
```

作用：根据房间号查房间。

返回 `Optional<Room>` 是为了表达“可能查不到”。

### deleteByCode(String roomCode)

```java
rooms.remove(roomCode);
```

作用：根据房间号删除房间。当前主要用于房主离开销毁房间。

### nextRoomCode()

作用：生成一个当前内存中不存在的 6 位房间号。

流程：

1. 调用 `randomRoomCode()` 生成随机码。
2. 用 `rooms.containsKey(roomCode)` 判断是否已经被使用。
3. 如果重复就继续生成。
4. 不重复就返回。

## RoomService 私有辅助方法

### findRoom(String roomCode)

作用：根据房间号找房间。

底层调用：

```java
roomRepository.findByCode(roomCode)
```

如果找不到，会抛：

```java
RoomException(RoomErrorCode.ROOM_NOT_FOUND, "Room not found.")
```

### findPlayer(Room room, String playerToken)

作用：根据 token 在房间里找当前玩家。

底层调用：

```java
room.findPlayerByToken(playerToken)
```

如果找不到，会抛：

```java
RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found.")
```

### snapshot(Room room)

作用：把内部可变的 `Room` 对象转换成对外返回的 `RoomSnapshot`。

为什么不直接返回 `Room`：

- `Room` 是内部领域对象，后续可能包含不想给前端看的数据。
- `Room` 内部有玩家列表和状态修改方法，不适合作为接口返回值。
- `RoomSnapshot` 是只读视图，不包含玩家 token。

## 当前接口汇总

| 方法 | 路径 | 参数 | 返回 | 主要修改 |
| --- | --- | --- | --- | --- |
| POST | `/api/rooms` | 无 | `CreateRoomResponse` | 新建 Room，`rooms.put(roomCode, room)` |
| POST | `/api/rooms/{roomCode}/join` | path: `roomCode` | `JoinRoomResponse` | 新增玩家，`rooms.put(roomCode, room)` |
| GET | `/api/rooms/{roomCode}/snapshot` | path: `roomCode` | `RoomSnapshot` | 不修改，只读 |
| POST | `/api/rooms/{roomCode}/start` | path: `roomCode`，header: `X-Player-Token` | `RoomSnapshot` | 改状态为 `CHATTING`，`rooms.put(roomCode, room)` |
| POST | `/api/rooms/{roomCode}/leave` | path: `roomCode`，header: `X-Player-Token` | `RoomSnapshot` | 普通玩家删除玩家；房主删除整个房间 |

