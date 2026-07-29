package com.example.noltok.friend.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class FriendKafkaConfig {

    public static final String FRIEND_DELETED_TOPIC = "friend.deleted";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // 파티션 3개: deletedBy를 key로 써서 같은 유저가 실행한 삭제는 항상 같은
    // 파티션으로 모임 (분석 시 유저 단위 집계에 유리 — 순서 보장이 목적은 아님)
    @Bean
    public NewTopic friendDeletedTopic() {
        return new NewTopic(FRIEND_DELETED_TOPIC, 3, (short) 1);
    }

    @Bean
    public ProducerFactory<String, FriendDeletedEvent> friendDeletedProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, FriendDeletedEvent> friendDeletedKafkaTemplate() {
        return new KafkaTemplate<>(friendDeletedProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, FriendDeletedEvent> friendDeletedConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "friend-deleted-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        config.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.example.noltok.friend.kafka");
        config.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, FriendDeletedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FriendDeletedEvent> friendDeletedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FriendDeletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(friendDeletedConsumerFactory());
        return factory;
    }
}
