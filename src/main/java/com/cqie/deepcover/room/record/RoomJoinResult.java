package com.cqie.deepcover.room.record;

/**
 * 加入房间后的结果。
 *
 * <p>每个浏览器都会拿到自己的 playerToken，后续请求通过 token 识别玩家。</p>
 */
public record RoomJoinResult(
        String roomCode,
        String playerId,
        String playerToken,
        RoomSnapshot snapshot
) {
}
