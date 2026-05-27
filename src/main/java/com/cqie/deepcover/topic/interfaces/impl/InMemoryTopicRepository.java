package com.cqie.deepcover.topic.interfaces.impl;

import com.cqie.deepcover.topic.interfaces.TopicRepository;
import com.cqie.deepcover.topic.record.Topic;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 内存话题仓库。
 *
 * <p>MVP 阶段直接使用固定话题列表，后续可以替换为数据库或配置文件。</p>
 */
@Repository
public class InMemoryTopicRepository implements TopicRepository {
    private static final List<Topic> TOPICS = List.of(
            new Topic("topic-001", "如果你必须用一种天气形容今天的心情，你会选什么？"),
            new Topic("topic-002", "最近一次让你印象很深的路人瞬间是什么？"),
            new Topic("topic-003", "如果房间里每个人都要开一家小店，你觉得自己会开什么？"),
            new Topic("topic-004", "你更愿意拥有很强的记忆力，还是很强的观察力？"),
            new Topic("topic-005", "如果今晚只能吃一种主食，你会选什么？"),
            new Topic("topic-006", "你觉得一个人最容易暴露性格的小习惯是什么？"),
            new Topic("topic-007", "如果要给现在这个房间起一个电影名，你会叫什么？"),
            new Topic("topic-008", "你更相信第一印象，还是相处后的判断？"),
            new Topic("topic-009", "如果突然多出一天假期，你最可能怎么安排？"),
            new Topic("topic-010", "你觉得聊天里最容易让人放松的话题是什么？")
    );

    @Override
    public List<Topic> findAll() {
        return TOPICS;
    }
}
