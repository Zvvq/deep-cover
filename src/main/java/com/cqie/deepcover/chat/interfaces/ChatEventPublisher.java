package com.cqie.deepcover.chat.interfaces;

import com.cqie.deepcover.chat.record.RoomEvent;

/**
 * 聊天事件发布接口。
 *
 * <p>业务层只调用这个接口，不关心底层是 WebSocket、消息队列还是别的推送方式。</p>
 */
public interface ChatEventPublisher {
    void publish(String roomCode, RoomEvent event);
}
