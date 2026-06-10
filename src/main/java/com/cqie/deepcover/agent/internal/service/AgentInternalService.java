package com.cqie.deepcover.agent.internal.service;

import com.cqie.deepcover.agent.internal.record.AgentMessageCommand;
import com.cqie.deepcover.agent.internal.record.AgentMessageView;
import com.cqie.deepcover.agent.internal.record.AgentPlayerView;
import com.cqie.deepcover.agent.internal.record.AgentRecentMessagesResponse;
import com.cqie.deepcover.agent.internal.record.AgentRoomStateResponse;
import com.cqie.deepcover.agent.internal.record.AgentVoteCommand;
import com.cqie.deepcover.agent.internal.record.AgentVoteStateResponse;
import com.cqie.deepcover.agent.internal.record.AgentWordDescriptionCommand;
import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.chat.service.ChatService;
import com.cqie.deepcover.room.enums.GameMode;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.vote.record.VoteResult;
import com.cqie.deepcover.vote.record.VoteSnapshot;
import com.cqie.deepcover.vote.service.VoteService;
import com.cqie.deepcover.word.record.PlayerWordResponse;
import com.cqie.deepcover.word.record.WordDescriptionResult;
import com.cqie.deepcover.word.record.WordDescriptionSnapshot;
import com.cqie.deepcover.word.service.WordDescriptionService;
import com.cqie.deepcover.word.service.WordGameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AgentInternalService.class);
    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final RoomService roomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final VoteService voteService;
    private final WordGameService wordGameService;
    private final WordDescriptionService wordDescriptionService;

    public AgentInternalService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            ChatService chatService,
            VoteService voteService,
            WordGameService wordGameService,
            WordDescriptionService wordDescriptionService
    ) {
        this.roomService = roomService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
        this.voteService = voteService;
        this.wordGameService = wordGameService;
        this.wordDescriptionService = wordDescriptionService;
    }

    public AgentRoomStateResponse roomState(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        AgentRoomStateResponse response = new AgentRoomStateResponse(
                room.roomCode(),
                room.status(),
                room.gameMode(),
                room.topic(),
                currentRound(room),
                aliveCount(room, PlayerType.HUMAN),
                aliveCount(room, PlayerType.AI),
                room.players().stream()
                        .map(player -> new AgentPlayerView(
                                player.id(),
                                player.number(),
                                player.color(),
                                player.type(),
                                player.alive(),
                                player.host()
                        ))
                        .toList()
        );
        log.info("Agent 查询房间状态，roomCode={}, status={}, topicId={}, roundNumber={}, aliveHumanCount={}, aliveAiCount={}",
                roomCode, response.status(), response.topic() == null ? null : response.topic().id(),
                response.roundNumber(), response.aliveHumanCount(), response.aliveAiCount());
        return response;
    }

    public PlayerWordResponse aiWord(String roomCode, String aiPlayerId) {
        Player aiPlayer = roomService.requireAliveAiWordParticipant(roomCode, aiPlayerId);
        PlayerWordResponse response = wordGameService.findPlayerWord(roomService.snapshot(roomCode), aiPlayer.id());
        log.info("Agent 查询 AI 关键词，roomCode={}, aiPlayerId={}, number={}, color={}",
                roomCode, aiPlayerId, response.number(), response.color());
        return response;
    }

    public AgentRecentMessagesResponse recentMessages(String roomCode, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<ChatMessage> messages = chatMessageRepository.findByRoomCode(roomCode);
        int fromIndex = Math.max(0, messages.size() - normalizedLimit);
        AgentRecentMessagesResponse response = new AgentRecentMessagesResponse(
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
        log.info("Agent 查询最近聊天记录，roomCode={}, requestedLimit={}, normalizedLimit={}, returnedMessageCount={}",
                roomCode, limit, normalizedLimit, response.messages().size());
        return response;
    }

    public AgentVoteStateResponse voteState(String roomCode) {
        VoteSnapshot snapshot = voteService.systemSnapshot(roomCode);
        RoomSnapshot room = roomService.snapshot(roomCode);
        AgentVoteStateResponse response = new AgentVoteStateResponse(
                roomCode,
                snapshot.roundNumber(),
                snapshot.submittedVoteCount(),
                snapshot.requiredVoteCount(),
                room.players().stream()
                        .filter(PlayerSnapshot::alive)
                        .map(PlayerSnapshot::id)
                        .toList()
        );
        log.info("Agent 查询投票状态，roomCode={}, roundNumber={}, submittedVoteCount={}, requiredVoteCount={}",
                roomCode, response.roundNumber(), response.submittedVoteCount(), response.requiredVoteCount());
        return response;
    }

    public WordDescriptionSnapshot wordDescriptions(String roomCode) {
        WordDescriptionSnapshot snapshot = wordDescriptionService.systemSnapshot(roomCode);
        log.info("Agent 查询关键词描述状态，roomCode={}, roundNumber={}, currentPlayerId={}, currentNumber={}, submittedDescriptionCount={}",
                roomCode, snapshot.roundNumber(), snapshot.currentPlayerId(), snapshot.currentNumber(),
                snapshot.descriptions().size());
        return snapshot;
    }

    public ChatMessageResponse sendMessage(String roomCode, AgentMessageCommand command) {
        ChatMessageResponse response = chatService.sendAiMessage(roomCode, command.aiPlayerId(), command.content());
        log.info("Agent 提交 AI 发言，roomCode={}, aiPlayerId={}, messageId={}, content={}",
                roomCode, command.aiPlayerId(), response.id(), response.content());
        return response;
    }

    public VoteResult castVote(String roomCode, AgentVoteCommand command) {
        VoteResult result = voteService.castSystemVote(roomCode, command.aiPlayerId(), command.targetPlayerId());
        log.info("Agent 提交 AI 投票，roomCode={}, aiPlayerId={}, targetPlayerId={}, settled={}, winner={}",
                roomCode, command.aiPlayerId(), command.targetPlayerId(), result.settled(), result.winner());
        return result;
    }

    public WordDescriptionResult submitWordDescription(String roomCode, AgentWordDescriptionCommand command) {
        Player aiPlayer = roomService.requireAliveAiWordParticipant(roomCode, command.aiPlayerId());
        WordDescriptionResult result = wordDescriptionService.submitSystemDescription(
                roomCode,
                aiPlayer.id(),
                command.content()
        );
        log.info("Agent 提交 AI 关键词描述，roomCode={}, aiPlayerId={}, roundNumber={}, content={}, votingStarted={}",
                roomCode, command.aiPlayerId(), result.roundNumber(), result.description().content(), result.votingStarted());
        return result;
    }

    private long aliveCount(RoomSnapshot room, PlayerType type) {
        return room.players().stream()
                .filter(player -> player.alive() && player.type() == type)
                .count();
    }

    private int currentRound(RoomSnapshot room) {
        if (room.gameMode() == GameMode.WORD_UNDERCOVER) {
            return wordGameService.currentRound(room.roomCode());
        }
        return voteService.currentRound(room.roomCode());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }
}
