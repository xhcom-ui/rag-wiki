package com.zhiwiki.document.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置 - 交换机、队列、绑定声明
 */
@Configuration
public class RabbitMQConfig {

    // ========== 交换机 ==========
    public static final String DOCUMENT_EXCHANGE = "document.exchange";
    public static final String PARSE_EXCHANGE = "parse.exchange";

    // ========== 队列 ==========
    public static final String DOCUMENT_PARSE_QUEUE = "document.parse.queue";
    public static final String VECTOR_EMBED_QUEUE = "vector.embed.queue";
    public static final String PARSE_RESULT_QUEUE = "parse.result.queue";

    // ========== 路由键 ==========
    public static final String DOCUMENT_PARSE_ROUTING_KEY = "document.parse";
    public static final String VECTOR_EMBED_ROUTING_KEY = "vector.embed";
    public static final String PARSE_RESULT_ROUTING_KEY = "parse.result";

    // 声明交换机
    @Bean
    public DirectExchange documentExchange() {
        return new DirectExchange(DOCUMENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange parseExchange() {
        return new DirectExchange(PARSE_EXCHANGE, true, false);
    }

    // 声明队列
    @Bean
    public Queue documentParseQueue() {
        return QueueBuilder.durable(DOCUMENT_PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlx.document.parse")
                .build();
    }

    @Bean
    public Queue vectorEmbedQueue() {
        return QueueBuilder.durable(VECTOR_EMBED_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlx.vector.embed")
                .build();
    }

    @Bean
    public Queue parseResultQueue() {
        return QueueBuilder.durable(PARSE_RESULT_QUEUE).build();
    }

    // 绑定关系
    @Bean
    public Binding documentParseBinding() {
        return BindingBuilder.bind(documentParseQueue())
                .to(documentExchange())
                .with(DOCUMENT_PARSE_ROUTING_KEY);
    }

    @Bean
    public Binding vectorEmbedBinding() {
        return BindingBuilder.bind(vectorEmbedQueue())
                .to(documentExchange())
                .with(VECTOR_EMBED_ROUTING_KEY);
    }

    @Bean
    public Binding parseResultBinding() {
        return BindingBuilder.bind(parseResultQueue())
                .to(parseExchange())
                .with(PARSE_RESULT_ROUTING_KEY);
    }
}
