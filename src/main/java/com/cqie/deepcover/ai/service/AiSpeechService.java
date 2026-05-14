package com.cqie.deepcover.ai.service;

import com.cqie.deepcover.ai.interfaces.AiDecisionClient;
import com.cqie.deepcover.ai.record.AiChatMessageContext;
import com.cqie.deepcover.ai.record.AiPlayerContext;
import com.cqie.deepcover.ai.record.AiSpeechDecisionRequest;
import com.cqie.deepcover.ai.record.AiSpeechDecisionResponse;
import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.chat.service.ChatService;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 发言业务服务。
 *
 * <p>定时器只负责触发，这里负责组装上下文、调用 AI 决策接口，以及把合法发言保存成聊天消息。</p>
 */
@Service
public class AiSpeechService {
    private final RoomService roomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final AiDecisionClient aiDecisionClient;
    private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();

    public AiSpeechService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            ChatService chatService,
            AiDecisionClient aiDecisionClient
    ) {
        this.roomService = roomService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
        this.aiDecisionClient = aiDecisionClient;
    }

    /**
     * 扫描所有聊天中的房间，并给每个存活 AI 一次决策机会。
     */
    public void triggerForChattingRooms() {
        for (RoomSnapshot room : roomService.snapshotsByStatus(RoomStatus.CHATTING)) {
            room.players().stream()
                    .filter(player -> player.alive() && player.type() == PlayerType.AI)
                    .forEach(ai -> trySpeak(room.roomCode(), ai.id()));
        }
    }

    private void trySpeak(String roomCode, String aiPlayerId) {
        String inFlightKey = roomCode + ":" + aiPlayerId;
        if (!inFlightKeys.add(inFlightKey)) {
            return;
        }

        try {
            RoomSnapshot room = roomService.snapshot(roomCode);
            if (room.status() != RoomStatus.CHATTING || !isAliveAi(room, aiPlayerId)) {
                return;
            }

            AiSpeechDecisionResponse response = aiDecisionClient.decideSpeech(new AiSpeechDecisionRequest(
                    roomCode,
                    aiPlayerId,
                    room.players().stream().map(this::toPlayerContext).toList(),
                    chatMessageRepository.findByRoomCode(roomCode).stream().map(this::toMessageContext).toList()
            ));
            if (response != null && response.shouldSpeak()) {
                chatService.sendAiMessage(roomCode, aiPlayerId, response.message());
            }
        } catch (RoomException ignored) {
            // 房间状态可能在 AI 决策期间变化，本轮跳过即可。
        } finally {
            inFlightKeys.remove(inFlightKey);
        }
    }

    private boolean isAliveAi(RoomSnapshot room, String playerId) {
        return room.players().stream()
                .anyMatch(player -> player.id().equals(playerId)
                        && player.alive()
                        && player.type() == PlayerType.AI);
    }

    private AiPlayerContext toPlayerContext(PlayerSnapshot player) {
        return new AiPlayerContext(player.id(), player.type(), player.alive());
    }

    private AiChatMessageContext toMessageContext(ChatMessage message) {
        return new AiChatMessageContext(message.senderPlayerId(), message.content(), message.createdAt());
    }
}
