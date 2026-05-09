package com.cqie.deepcover.chat.controller;

import com.cqie.deepcover.chat.record.ChatMessageRequest;
import com.cqie.deepcover.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 聊天入口。
 *
 * <p>前端发送到 `/app/rooms/{roomCode}/chat`，Spring 会根据这里的 `@MessageMapping`
 * 把消息路由到当前方法。真正的保存和广播逻辑都放在 {@link ChatService} 里。</p>
 */
@Controller
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/rooms/{roomCode}/chat")
    public void sendMessage(
            @DestinationVariable String roomCode,
            @Payload ChatMessageRequest request
    ) {
        chatService.sendMessage(roomCode, request);
    }
}
