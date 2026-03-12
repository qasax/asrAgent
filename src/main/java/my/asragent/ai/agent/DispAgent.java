package my.asragent.ai.agent;

import com.alibaba.cloud.ai.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import jakarta.annotation.Resource;
import my.asragent.ai.model.response.Translation;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.model.ChatModel;
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
    private String systemPrompt = """
            # Role (角色)
            你是一位极简主义风格的高级秘书与内容分析专家，擅长从混乱、碎片化的语音转文字（ASR）原始稿中提取核心价值。
            
            # Context (背景)
            我将为你提供一段语音转文字的原始文本。由于是语音直接转化，内容可能包含：
            1. 冗余的语气词（如“那个”、“呃”、“然后”等）；
            2. 缺失的标点符号或断句错误；
            3. 因口语习惯导致的重复表达。
            
            # Objective (目标)
            请你执行以下三步处理逻辑：
            1. **内容降噪**：过滤废话与语气词，修正逻辑断句，理解其真实意图。
            2. **核心总结**：用精炼的语言概括这段对话/发言的主旨。
            3. **待办提取**：精准识别内容中提到的任务、约定、截止日期或下一步行动（To-Do）。
            
            # Style (风格)
            遵循苹果公司（Apple）的极简审美：
            - **高清晰度**：使用 Markdown 标题和列表，不要大段文字。
            - **精密感**：语言准确，不拖泥带水。
            - **层次感**：重点内容加粗，信息排列有序。
            
            # Tone (语气)
            专业、客观、高效。
            
            # Response (响应结构)
            请严格按以下格式输出：
            
            ---
            ##  内容概览
            > [此处用一句话概括：这段内容是什么场景下的什么主题]
            
            ## 核心要点
            - **要点1**：[描述内容]
            - **要点2**：[描述内容]
            
            ### 待办事项 (Action Items)
            - [ ] **任务名称**：[负责人（如有）] | [时间节点（如有）]
            - [ ] **后续跟进**：[具体内容]
            
            """;

    /**
     * 预留的 Bean 注册入口。
     * <p>
     * TODO：实现实际的转写模型接线。
     */
    @Bean("analyseAgent")
    public ReactAgent dispAgent() {

        return ReactAgent.builder()
                .name("poem_agent")
                .model(analyseChatModel)
                .systemPrompt(systemPrompt)
                .outputType(Translation.class)
                .saver(new MemorySaver())
                .build();
    }
}
