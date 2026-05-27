package com.cqie.deepcover.room.record;

import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.topic.record.TopicSnapshot;

import java.util.List;

/**
 * 房间当前状态快照。
 *
 * <p>REST 接口和后续 WebSocket 事件都可以复用这个结构，前端刷新页面时
 * 也依靠它恢复当前房间状态。</p>
 */
public record RoomSnapshot(
        String roomCode,
        RoomStatus status,
        String hostPlayerId,
        List<PlayerSnapshot> players,
        TopicSnapshot topic
) {
    public RoomSnapshot {
        // 防止外部持有原始 List 后继续修改快照内容。
        players = List.copyOf(players);
    }

    public RoomSnapshot(
            String roomCode,
            RoomStatus status,
            String hostPlayerId,
            List<PlayerSnapshot> players
    ) {
        this(roomCode, status, hostPlayerId, players, null);
    }
}
