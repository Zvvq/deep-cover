package com.cqie.deepcover.topic.service;

import com.cqie.deepcover.topic.interfaces.TopicRepository;
import com.cqie.deepcover.topic.record.Topic;
import com.cqie.deepcover.topic.record.TopicSnapshot;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * 话题服务，负责从话题池中抽取当前聊天话题。
 */
@Service
public class TopicService {
    private final TopicRepository topicRepository;
    private final Random random = new SecureRandom();

    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    public TopicSnapshot randomTopic() {
        List<Topic> topics = topicRepository.findAll();
        if (topics.isEmpty()) {
            throw new IllegalStateException("Topic pool cannot be empty.");
        }
        return TopicSnapshot.from(topics.get(random.nextInt(topics.size())));
    }
}
