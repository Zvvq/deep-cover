# 聊天模块实现计划

## 目标

开始游戏后，房间状态进入 `CHATTING`，玩家可以在房间内发送聊天消息。聊天模块先完成真人玩家的实时消息发送与广播，后续 AI Agent 可以复用同一套消息写入和广播能力。

## 模块边界

- 房间模块负责：房间是否存在、玩家 token 是否有效、房间是否已经开始。
- 聊天模块负责：校验聊天内容、保存消息、把消息广播给同房间玩家。
- 前端先做简约接入：开始后显示聊天区，通过 WebSocket/STOMP 发送和接收消息。

## 后端结构

- `chat.config.WebSocketConfig`：注册 WebSocket 入口 `/ws`，配置应用消息前缀 `/app` 和房间广播前缀 `/topic`。
- `chat.controller.ChatController`：接收 `/app/rooms/{roomCode}/chat` 的聊天消息。
- `chat.service.ChatService`：聊天业务入口，校验房间状态与玩家身份，写入消息并广播事件。
- `chat.interfaces.ChatMessageRepository`：聊天消息仓库接口。
- `chat.interfaces.ChatEventPublisher`：聊天事件发布接口。
- `chat.interfaces.impl.InMemoryChatMessageRepository`：内存版聊天消息仓库，后续可以替换数据库。
- `chat.interfaces.impl.SimpChatEventPublisher`：基于 Spring WebSocket 的事件发布实现。
- `chat.record.*`：聊天请求、消息快照、房间事件等不可变数据对象。

## API / WebSocket

- WebSocket 连接地址：`/ws`
- 订阅房间事件：`/topic/rooms/{roomCode}/events`
- 发送聊天消息：`/app/rooms/{roomCode}/chat`

发送消息体：

```json
{
  "playerToken": "玩家令牌",
  "content": "聊天内容"
}
```

广播事件体：

```json
{
  "type": "CHAT_MESSAGE",
  "payload": {
    "id": "消息ID",
    "roomCode": "房间号",
    "senderPlayerId": "发送者ID",
    "content": "聊天内容",
    "createdAt": "发送时间"
  }
}
```

## 测试点

- 房间未开始时不能发送聊天消息。
- 房间进入 `CHATTING` 后，有效玩家可以发送消息。
- 发送成功后消息会保存到内存仓库。
- 发送成功后会广播 `CHAT_MESSAGE` 事件。
