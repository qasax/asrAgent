package my.asragent.ai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import jakarta.annotation.Resource;
import my.asragent.ai.hooks.RAGAgentHook;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.model.structModel.Translation;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

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
    @Value("classpath:/prompts/summary.st")
    private String summaryPrompt;
    @Value("classpath:/prompts/finalSummary.st")
    private String  finalSummaryPrompt;
    @Value("classpath:/prompts/imagePlan.st")
    private String imagePlanPrompt;
    @Value("classpath:/prompts/qa.st")
    private String qaPrompt;
    @Resource
    private RAGAgentHook ragAgentHook;
    @Resource
    private RedissonClient redissonClient;
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

    /**
     *
     * 阶段总结agent
     */
    @Bean("summaryAgent")
    public ReactAgent summaryAgent() {
        return ReactAgent.builder()
                .name("summaryAgent")
                .model(analyseChatModel)
                .systemPrompt(summaryPrompt)
                .build();
    }
    /**
     *
     * 最终总结agent
     */
    @Bean("finalSummaryAgent")
    public ReactAgent finalSummaryAgent() {
        return ReactAgent.builder()
                .name("finalSummaryAgent")
                .model(analyseChatModel)
                .systemPrompt(finalSummaryPrompt)
                .build();
    }
    /**
     * 图片生成计划
     *
     */
    @Bean("imgPlanAgent")
    public ReactAgent imgPlanAgent() {
        return ReactAgent.builder()
                .name("imgPlanAgent")
                .model(analyseChatModel)
                .outputType(ImageGenerationDecision.class)
                .systemPrompt(imagePlanPrompt)
                .build();
    }

    /**
     *
     * 问答agent
     */
    @Bean("qaAgent")
    public ReactAgent qaAgent() {
        return ReactAgent.builder()
                .name("qaAgent")
                .model(analyseChatModel)
                .hooks(ragAgentHook)
                .systemPrompt(qaPrompt)
                .saver(RedisSaver.builder().redisson(redissonClient).build())
                .build();
    }


    /**
     *
     * 关键词生图Agen
     */
    @Bean("keyimageAgent")
    public ReactAgent keyimageAgent() {
        return ReactAgent.builder()
                .name("keyimageAgent")
                .model(analyseChatModel)
                .hooks(ragAgentHook)
                .systemPrompt(qaPrompt)
                .saver(RedisSaver.builder().redisson(redissonClient).build())
                .build();
    }


}
