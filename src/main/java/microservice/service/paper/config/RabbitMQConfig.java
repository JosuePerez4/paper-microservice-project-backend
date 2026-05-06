package microservice.service.paper.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${RABBITMQ_EXCHANGE}")
    private String exchangeName;

    @Value("${RABBITMQ_ROUTING_KEY_EVALUATED}")
    private String routingKey;

    public static String EXCHANGE;
    public static String ROUTING_KEY_EVALUATED;

    @Value("${RABBITMQ_EXCHANGE}")
    public void setExchange(String exchange) {
        EXCHANGE = exchange;
    }

    @Value("${RABBITMQ_ROUTING_KEY_EVALUATED}")
    public void setRoutingKey(String rk) {
        ROUTING_KEY_EVALUATED = rk;
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
