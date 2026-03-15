package my.asragent.ai.chatClient;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispChatClient {
    @Resource
    public ChatModel analyseChatModel;
    @Bean(name = "rewriteChatClient")
    public ChatClient rewriteChatClient() {
        return ChatClient
                .builder(analyseChatModel)
                .build();
    }
}
