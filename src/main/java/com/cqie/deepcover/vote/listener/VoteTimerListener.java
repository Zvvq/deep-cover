package com.cqie.deepcover.vote.listener;

import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.record.GameTimerExpiredEvent;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.vote.service.VoteService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听计时器到期事件，驱动聊天阶段和投票阶段之间的流转。
 */
@Component
public class VoteTimerListener {
    private final VoteService voteService;

    public VoteTimerListener(VoteService voteService) {
        this.voteService = voteService;
    }

    @EventListener
    public void onGameTimerExpired(GameTimerExpiredEvent event) {
        try {
            if (event.phase() == GamePhase.CHATTING) {
                voteService.startVoting(event.roomCode());
            } else if (event.phase() == GamePhase.VOTING) {
                voteService.settleVoting(event.roomCode());
            }
        } catch (RoomException ignored) {
            // 房间可能已经结束或被房主销毁，计时器事件不应该让后台任务失败。
        }
    }
}
