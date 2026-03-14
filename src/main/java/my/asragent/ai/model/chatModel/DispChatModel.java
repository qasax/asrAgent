package my.asragent.ai.model.chatModel;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispChatModel {
    @Value("${spring.ai.dashscope.chat.options.model}")
    private String modelName;
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    @Value("${spring.ai.dashscope.chat.options.multi-model}")
    private Boolean  multiModel;

    @Bean("analyseChatModel")
    public ChatModel chatModel() {
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .multiModel(multiModel)
                .withModel(modelName)           // 模型名称
                .withTemperature(0.5)              // Temperature 参数
                .withMaxToken(2000)                // 最大令牌数
                .withTopP(0.9)                     // Top-P 采样
                .build();
        // 创建 ChatModel
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }
}
