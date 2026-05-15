package com.cqie.deepcover.agent.internal.service;

import com.cqie.deepcover.agent.internal.record.AgentMessageCommand;
import com.cqie.deepcover.agent.internal.record.AgentMessageView;
import com.cqie.deepcover.agent.internal.record.AgentPlayerView;
import com.cqie.deepcover.agent.internal.record.AgentRecentMessagesResponse;
import com.cqie.deepcover.agent.internal.record.AgentRoomStateResponse;
import com.cqie.deepcover.agent.internal.record.AgentVoteCommand;
import com.cqie.deepcover.agent.internal.record.AgentVoteStateResponse;
import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.chat.service.ChatService;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.vote.record.VoteResult;
import com.cqie.deepcover.vote.record.VoteSnapshot;
import com.cqie.deepcover.vote.service.VoteService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 给 Python Agent 使用的内部能力服务。
 *
 * <p>这里返回机器友好的结构，不复用前端 DTO。Agent 可以查状态和提交动作，
 * 但具体规则校验仍然交给 room/chat/vote 模块。</p>
 */
@Service
public class AgentInternalService {
    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final RoomService roomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final VoteService voteService;

    public AgentInternalService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            ChatService chatService,
            VoteService voteService
    ) {
        this.roomService = roomService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
        this.voteService = voteService;
    }

    public AgentRoomStateResponse roomState(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        return new AgentRoomStateResponse(
                room.roomCode(),
                room.status(),
                voteService.currentRound(roomCode),
                aliveCount(room, PlayerType.HUMAN),
                aliveCount(room, PlayerType.AI),
                room.players().stream()
                        .map(player -> new AgentPlayerView(
                                player.id(),
                                player.type(),
                                player.alive(),
                                player.host()
                        ))
                        .toList()
        );
    }

    public AgentRecentMessagesResponse recentMessages(String roomCode, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<ChatMessage> messages = chatMessageRepository.findByRoomCode(roomCode);
        int fromIndex = Math.max(0, messages.size() - normalizedLimit);
        return new AgentRecentMessagesResponse(
                roomCode,
                messages.subList(fromIndex, messages.size()).stream()
                        .map(message -> new AgentMessageView(
                                message.id(),
                                message.senderPlayerId(),
                                message.content(),
                                message.createdAt()
                        ))
                        .toList()
        );
    }

    public AgentVoteStateResponse voteState(String roomCode) {
        VoteSnapshot snapshot = voteService.systemSnapshot(roomCode);
        RoomSnapshot room = roomService.snapshot(roomCode);
        return new AgentVoteStateResponse(
                roomCode,
                snapshot.roundNumber(),
                snapshot.submittedVoteCount(),
                snapshot.requiredVoteCount(),
                room.players().stream()
                        .filter(PlayerSnapshot::alive)
                        .map(PlayerSnapshot::id)
                        .toList()
        );
    }

    public ChatMessageResponse sendMessage(String roomCode, AgentMessageCommand command) {
        return chatService.sendAiMessage(roomCode, command.aiPlayerId(), command.content());
    }

    public VoteResult castVote(String roomCode, AgentVoteCommand command) {
        return voteService.castSystemVote(roomCode, command.aiPlayerId(), command.targetPlayerId());
    }

    private long aliveCount(RoomSnapshot room, PlayerType type) {
        return room.players().stream()
                .filter(player -> player.alive() && player.type() == type)
                .count();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }
}
