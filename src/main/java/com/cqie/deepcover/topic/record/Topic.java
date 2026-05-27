package com.cqie.deepcover.topic.record;

/**
 * 游戏聊天话题。
 *
 * <p>当前话题先放在内存里，后续接数据库时保持这个领域对象不变即可。</p>
 */
public record Topic(
        String id,
        String content
) {
}
