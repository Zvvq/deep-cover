package com.cqie.deepcover.topic.record;

/**
 * 对前端和 Agent 可见的话题快照。
 */
public record TopicSnapshot(
        String id,
        String content
) {
    public static TopicSnapshot from(Topic topic) {
        return new TopicSnapshot(topic.id(), topic.content());
    }
}
