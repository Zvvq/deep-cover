package com.cqie.deepcover.chat.service;

import com.cqie.deepcover.chat.enums.RoomEventType;
import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.chat.record.ChatMessageRequest;
import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.chat.record.RoomEvent;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.service.RoomService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * chat 模块的业务服务。
 *
 * <p>它负责“能不能发、发什么、保存到哪里、广播什么”。房间状态和玩家 token 的校验委托给
 * {@link RoomService}，这样 room 模块仍然是房间规则的唯一入口。</p>
 */
@Service
public class ChatService {
    private static final int MAX_CONTENT_LENGTH = 300;

    private final RoomService roomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatEventPublisher chatEventPublisher;

    public ChatService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            ChatEventPublisher chatEventPublisher
    ) {
        this.roomService = roomService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatEventPublisher = chatEventPublisher;
    }

    /**
     * 发送一条聊天消息。
     *
     * @param roomCode 房间码
     * @param request 前端传入的聊天请求
     * @return 已保存并广播的消息响应
     */
    public ChatMessageResponse sendMessage(String roomCode, ChatMessageRequest request) {
        String content = normalizeContent(request);
        Player sender = roomService.requireChatParticipant(roomCode, request.playerToken());

        ChatMessage message = new ChatMessage(
                nextMessageId(),
                roomCode,
                sender.id(),
                content,
                Instant.now()
        );
        chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.from(message);
        chatEventPublisher.publish(roomCode, new RoomEvent(RoomEventType.CHAT_MESSAGE, response));
        return response;
    }

    /**
     * 查询当前房间的全部历史聊天消息。
     *
     * <p>查询前也会校验玩家 token，避免没有加入房间的人只靠房间号就拉到聊天记录。</p>
     *
     * @param roomCode 房间码
     * @param playerToken 当前玩家凭证
     * @return 当前房间的全部聊天消息
     */
    public List<ChatMessageResponse> findMessages(String roomCode, String playerToken) {
        roomService.requireChatParticipant(roomCode, playerToken);
        return chatMessageRepository.findByRoomCode(roomCode).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /**
     * 统一处理聊天内容的空值、空白和长度限制。
     *
     * @param request 聊天请求
     * @return 清理后的聊天内容
     */
    private String normalizeContent(ChatMessageRequest request) {
        if (request == null || request.content() == null) {
            throw new RoomException(RoomErrorCode.INVALID_CHAT_MESSAGE, "Chat message cannot be empty.");
        }

        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new RoomException(RoomErrorCode.INVALID_CHAT_MESSAGE, "Chat message cannot be empty.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new RoomException(RoomErrorCode.INVALID_CHAT_MESSAGE, "Chat message is too long.");
        }
        return content;
    }

    private String nextMessageId() {
        return "message-" + UUID.randomUUID();
    }
}
