package my.asragent.hooks;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RAGMessagesHook  extends MessagesModelHook {
    @Resource
    private VectorStore vectorStore;
    @Override
    public String getName() {
        return "rag_messages_hook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 从消息中提取用户问题
        UserMessage userQuestion = extractUserQuestion(previousMessages);
        if (userQuestion == null) {
            return new AgentCommand(previousMessages);
        }
        Map<String, Object> metadata = userQuestion.getMetadata();
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        //只检索该用户当此会话信息
        Filter.Expression finalExp = b.and(
                b.eq("translation_record_id", metadata.get("translation_record_id").toString()),
                b.eq("user_id", metadata.get("user_id").toString())
        ).build();

        // Step 1: 检索相关文档
        List<Document> relevantDocs = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(userQuestion.getText())
                        .filterExpression(finalExp)
                        .topK(5)
                        .build()
        );

        // Step 2: 构建上下文
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining(","));

                        // Step 3: 构建增强的消息列表
                        List<Message> enhancedMessages = new ArrayList<>();

        // 添加系统提示（包含检索到的上下文）
        String systemPrompt = String.format("""
          你是一个有用的助手。基于以下上下文回答问题。
          如果上下文中没有相关信息，请说明你不知道。
          
          上下文：
          %s
          """, context);
        enhancedMessages.add(new SystemMessage(systemPrompt));

        // 保留原有的消息
        enhancedMessages.addAll(previousMessages);

        // 使用 REPLACE 策略替换消息
        return new AgentCommand(enhancedMessages, UpdatePolicy.REPLACE);
    }

    private UserMessage extractUserQuestion(List<Message> messages) {
        // 从消息列表中提取最后一个用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return ((UserMessage) msg);
            }
        }
        return null;
    }
}

