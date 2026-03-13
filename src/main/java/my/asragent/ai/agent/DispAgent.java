package my.asragent.ai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import jakarta.annotation.Resource;
import my.asragent.ai.model.response.Translation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 代理接线的占位类。
 * <p>
 * 该类当前不暴露任何可用的 Bean；保留为后续扩展的桩位。
 */
@Configuration
public class DispAgent {
    @Resource
    private ChatModel analyseChatModel;
    @Value("classpath:/prompts/system-v1.st")
    private String analyseSystemPrompt;
    @Value("classpath:/prompts/quickTranslate.st")
    private String quickTranslateSystemPrompt;
    @Value("classpath:/prompts/translate.st")
    private String translateSystemPrompt;
    /**
     * 预留的 Bean 注册入口。
     * <p>
     * TODO：实现实际的转写模型接线。
     */
    @Bean("analyseAgent")
    public ReactAgent analyseAgent() {

        return ReactAgent.builder()
                .name("analyseAgent")
                .model(analyseChatModel)
                .systemPrompt(analyseSystemPrompt)
                .outputType(Translation.class)
                .saver(new MemorySaver())
                .build();
    }
    /**
     *
     * 快速翻译agent
     */
    @Bean("quickTranslateAgent")
    public ReactAgent quickTranslateAgent() {
        return ReactAgent.builder()
                .name("quickTranslateAgent")
                .model(analyseChatModel)
                .systemPrompt(quickTranslateSystemPrompt)
                .build();
    }
    /**
     *
     * 翻译专家agent
     */
    @Bean("translateAgent")
    public ReactAgent translateAgent() {
        return ReactAgent.builder()
                .name("translateAgent")
                .model(analyseChatModel)
                .systemPrompt(translateSystemPrompt)
                .build();
    }
}
