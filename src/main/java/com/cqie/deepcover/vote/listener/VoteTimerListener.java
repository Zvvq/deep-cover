package com.cqie.deepcover.vote.listener;

import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.record.GameTimerExpiredEvent;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.vote.service.VoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听计时器到期事件，驱动聊天阶段和投票阶段之间的流转。
 */
@Component
public class VoteTimerListener {
    private static final Logger log = LoggerFactory.getLogger(VoteTimerListener.class);

    private final VoteService voteService;

    public VoteTimerListener(VoteService voteService) {
        this.voteService = voteService;
    }

    @EventListener
    public void onGameTimerExpired(GameTimerExpiredEvent event) {
        try {
            if (event.phase() == GamePhase.CHATTING) {
                log.info("聊天计时器到期，准备进入投票阶段，roomCode={}", event.roomCode());
                voteService.startVoting(event.roomCode());
            } else if (event.phase() == GamePhase.VOTING) {
                log.info("投票计时器到期，准备结算投票，roomCode={}", event.roomCode());
                voteService.settleVoting(event.roomCode());
            }
        } catch (RoomException ex) {
            log.warn("处理计时器到期事件失败，可能房间已结束或销毁，roomCode={}, phase={}, errorCode={}",
                    event.roomCode(), event.phase(), ex.getErrorCode());
            // 房间可能已经结束或被房主销毁，计时器事件不应该让后台任务失败。
        }
    }
}
