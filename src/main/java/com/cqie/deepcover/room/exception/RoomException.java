package com.cqie.deepcover.room.exception;

import com.cqie.deepcover.room.enums.RoomErrorCode;

/**
 * room 模块的业务异常。
 *
 * <p>使用 errorCode 而不是让前端解析 message，方便以后国际化或改文案。</p>
 */
public class RoomException extends RuntimeException {
    private final RoomErrorCode errorCode;

    public RoomException(RoomErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RoomErrorCode getErrorCode() {
        return errorCode;
    }
}
