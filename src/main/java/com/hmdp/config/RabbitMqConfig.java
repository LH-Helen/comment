package com.hmdp.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    private static final int INITIAL_CONCURRENT_CONSUMERS = 10;
    private static final int MAX_CONCURRENT_CONSUMERS = 10;

    @Bean("customContainerFactory")
    public SimpleRabbitListenerContainerFactory containerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory){
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConcurrentConsumers(INITIAL_CONCURRENT_CONSUMERS);
        factory.setMaxConcurrentConsumers(MAX_CONCURRENT_CONSUMERS);
        configurer.configure(factory, connectionFactory);
        return factory;
    }
}
