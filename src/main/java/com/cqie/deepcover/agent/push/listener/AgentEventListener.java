package com.cqie.deepcover.agent.push.listener;

import com.cqie.deepcover.agent.event.AgentChatMessagePayload;
import com.cqie.deepcover.agent.event.AgentEventType;
import com.cqie.deepcover.agent.event.AgentGameEndedPayload;
import com.cqie.deepcover.agent.event.AgentPlayerEliminatedPayload;
import com.cqie.deepcover.agent.event.AgentRoomDestroyedPayload;
import com.cqie.deepcover.agent.event.AgentRoomStartedPayload;
import com.cqie.deepcover.agent.event.AgentRoundStartedPayload;
import com.cqie.deepcover.agent.event.AgentVotingStartedPayload;
import com.cqie.deepcover.agent.push.service.AgentEventPushService;
import com.cqie.deepcover.chat.record.ChatMessageCreatedEvent;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomDestroyedEvent;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.record.RoomStartedEvent;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.vote.record.GameEndedEvent;
import com.cqie.deepcover.vote.record.PlayerEliminatedEvent;
import com.cqie.deepcover.vote.record.RoundStartedEvent;
import com.cqie.deepcover.vote.record.VotingStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 把 Java 内部事件转换为 Python Agent 能理解的事件。
 */
@Component
public class AgentEventListener {
    private final AgentEventPushService agentEventPushService;
    private final RoomService roomService;

    public AgentEventListener(AgentEventPushService agentEventPushService, RoomService roomService) {
        this.agentEventPushService = agentEventPushService;
        this.roomService = roomService;
    }

    @EventListener
    public void onRoomStarted(RoomStartedEvent event) {
        RoomSnapshot room = roomService.snapshot(event.roomCode());
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.ROOM_STARTED,
                new AgentRoomStartedPayload(
                        event.roomCode(),
                        room.players().stream()
                                .filter(player -> player.type() == PlayerType.AI)
                                .map(PlayerSnapshot::id)
                                .toList()
                )
        );
    }

    @EventListener
    public void onChatMessageCreated(ChatMessageCreatedEvent event) {
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.CHAT_MESSAGE,
                new AgentChatMessagePayload(
                        event.message().id(),
                        event.message().senderPlayerId(),
                        event.message().content(),
                        event.message().createdAt()
                )
        );
    }

    @EventListener
    public void onVotingStarted(VotingStartedEvent event) {
        RoomSnapshot room = roomService.snapshot(event.roomCode());
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.VOTING_STARTED,
                new AgentVotingStartedPayload(
                        event.roomCode(),
                        event.roundNumber(),
                        room.players().stream()
                                .filter(PlayerSnapshot::alive)
                                .map(PlayerSnapshot::id)
                                .toList()
                )
        );
    }

    @EventListener
    public void onPlayerEliminated(PlayerEliminatedEvent event) {
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.PLAYER_ELIMINATED,
                new AgentPlayerEliminatedPayload(event.playerId(), event.playerType())
        );
    }

    @EventListener
    public void onRoundStarted(RoundStartedEvent event) {
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.ROUND_STARTED,
                new AgentRoundStartedPayload(event.roundNumber())
        );
    }

    @EventListener
    public void onGameEnded(GameEndedEvent event) {
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.GAME_ENDED,
                new AgentGameEndedPayload(event.winner(), event.reason())
        );
    }

    @EventListener
    public void onRoomDestroyed(RoomDestroyedEvent event) {
        agentEventPushService.push(
                event.roomCode(),
                AgentEventType.ROOM_DESTROYED,
                new AgentRoomDestroyedPayload(event.reason())
        );
    }
}
