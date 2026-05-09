package com.cqie.deepcover.room.record;

/**
 * 创建房间后的结果。
 *
 * <p>playerToken 只返回给当前创建者，用于后续房主操作鉴权。</p>
 */
public record RoomCreateResult(
        String roomCode,
        String playerId,
        String playerToken,
        RoomSnapshot snapshot
) {
}
