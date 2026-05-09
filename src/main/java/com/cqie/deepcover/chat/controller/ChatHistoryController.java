package com.cqie.deepcover.chat.controller;

import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.chat.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聊天记录查询接口。
 *
 * <p>实时聊天走 WebSocket，历史消息查询走普通 HTTP。这样刷新页面或重新进入房间时，
 * 前端可以先拉取历史消息，再继续订阅 WebSocket 新消息。</p>
 */
@RestController
public class ChatHistoryController {
    private static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final ChatService chatService;

    public ChatHistoryController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取当前房间的全部聊天消息。
     *
     * @param roomCode 房间码
     * @param playerToken 当前玩家凭证
     * @return 当前房间的全部聊天消息
     */
    @GetMapping("/api/rooms/{roomCode}/messages")
    public List<ChatMessageResponse> findMessages(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken
    ) {
        return chatService.findMessages(roomCode, playerToken);
    }
}
