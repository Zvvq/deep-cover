package com.cqie.deepcover.chat.service;

import com.cqie.deepcover.chat.enums.RoomEventType;
import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.chat.record.ChatMessageCreatedEvent;
import com.cqie.deepcover.chat.record.ChatMessageRequest;
import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.chat.record.RoomEvent;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * chat 模块的业务服务。
 *
 * <p>真人消息和 AI 消息最终都走同一套保存和广播逻辑，前端只需要处理统一的
 * CHAT_MESSAGE 事件。</p>
 */
@Service
public class ChatService {
    private static final int MAX_CONTENT_LENGTH = 300;

    private final RoomService roomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatEventPublisher chatEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            ChatEventPublisher chatEventPublisher
    ) {
        this(roomService, chatMessageRepository, chatEventPublisher, event -> {
        });
    }

    @Autowired
    public ChatService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            ChatEventPublisher chatEventPublisher,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.roomService = roomService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatEventPublisher = chatEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 真人玩家发送聊天消息。
     */
    public ChatMessageResponse sendMessage(String roomCode, ChatMessageRequest request) {
        if (request == null) {
            throw new RoomException(RoomErrorCode.INVALID_CHAT_MESSAGE, "Chat message cannot be empty.");
        }
        String content = normalizeContent(request.content());
        Player sender = roomService.requireChatParticipant(roomCode, request.playerToken());

        return saveAndPublish(roomCode, sender.id(), content);
    }

    /**
     * AI 决策模块使用的内部发言入口。
     */
    public ChatMessageResponse sendAiMessage(String roomCode, String aiPlayerId, String content) {
        String normalizedContent = normalizeContent(content);
        Player sender = roomService.requireAliveAiChatParticipant(roomCode, aiPlayerId);

        return saveAndPublish(roomCode, sender.id(), normalizedContent);
    }

    /**
     * 查询当前房间的全部历史聊天消息。
     */
    public List<ChatMessageResponse> findMessages(String roomCode, String playerToken) {
        roomService.requireChatParticipant(roomCode, playerToken);
        return chatMessageRepository.findByRoomCode(roomCode).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    private ChatMessageResponse saveAndPublish(String roomCode, String senderPlayerId, String content) {
        ChatMessage message = new ChatMessage(
                nextMessageId(),
                roomCode,
                senderPlayerId,
                content,
                Instant.now()
        );
        chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.from(message);
        chatEventPublisher.publish(roomCode, new RoomEvent(RoomEventType.CHAT_MESSAGE, response));
        applicationEventPublisher.publishEvent(new ChatMessageCreatedEvent(roomCode, response));
        return response;
    }

    private String normalizeContent(String rawContent) {
        if (rawContent == null) {
            throw new RoomException(RoomErrorCode.INVALID_CHAT_MESSAGE, "Chat message cannot be empty.");
        }

        String content = rawContent.trim();
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
