package com.cqie.deepcover.room.record;

import org.springframework.stereotype.Component;

/**
 * room REST 接口使用的响应 DTO。
 *
 * <p>领域层的 result/snapshot 保持业务语义，Controller 再转换成 HTTP 响应。
 * 这样以后前端字段需要调整时，不会直接污染服务层。</p>
 */

public final class RoomDtos {
    private RoomDtos() {
    }

    public record CreateRoomResponse(
            String roomCode,
            String playerId,
            String playerToken,
            RoomSnapshot snapshot
    ) {
        public static CreateRoomResponse from(RoomCreateResult result) {
            return new CreateRoomResponse(
                    result.roomCode(),
                    result.playerId(),
                    result.playerToken(),
                    result.snapshot()
            );
        }
    }

    public record JoinRoomResponse(
            String roomCode,
            String playerId,
            String playerToken,
            RoomSnapshot snapshot
    ) {
        public static JoinRoomResponse from(RoomJoinResult result) {
            return new JoinRoomResponse(
                    result.roomCode(),
                    result.playerId(),
                    result.playerToken(),
                    result.snapshot()
            );
        }
    }

    public record ErrorResponse(
            String errorCode,
            String message
    ) {
    }
}
