package com.box.reservation.config;

import com.box.reservation.ai.tool.ScheduleTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Bean
    ChatClient reservationChatClient(ChatClient.Builder builder, ScheduleTools tools, ChatMemory chatMemory) {
        return builder.defaultTools(tools).defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();   // 全局唯一一份
    }

    @Bean
    ChatMemory chatMemory(){
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }
}
