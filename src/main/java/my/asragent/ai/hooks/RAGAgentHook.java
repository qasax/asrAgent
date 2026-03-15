package my.asragent.ai.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
@Slf4j
@Configuration
public class RAGAgentHook extends AgentHook {
    @Resource
    private VectorStore vectorStore;
    @Resource
    public ChatModel analyseChatModel;

    @Override
    public String getName() {
        return "rag_agent_hook";
    }



    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config)  {
        // 通过 OverAllState 获取 messages
        Optional<Object> messagesOpt = state.value("messages");
        if (!messagesOpt.isPresent()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        List<Message> messages = (List<Message>) messagesOpt.get();

        Map<String, Object> metadata = config.metadata().get();
        // 提取最后一个用户消息
        UserMessage userQuestion = extractUserQuestion(messages);

        log.info("RAGMessagesHook-beforeModel translation_record_id:{}",metadata.get("translation_record_id").toString());
        // 重写用户提示词，提高检索效果
        RewriteQueryTransformer rewriteQueryTransformer =
                RewriteQueryTransformer.builder()
                        .chatClientBuilder(ChatClient.builder(analyseChatModel))
                        .build();
        Query userQuery = new Query(userQuestion.getText());
        Query transformed = rewriteQueryTransformer.transform(userQuery);
        // 构建过滤条件
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression finalExp = b.and(
                b.eq("translation_record_id", metadata.get("translation_record_id").toString()),
                b.eq("user_id", metadata.get("user_id").toString())
        ).build();

        // 检索向量库
        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.1)
                .topK(5)
                .filterExpression(finalExp)
                .build();
        List<Document> retrieveDocuments = retriever.retrieve(transformed);

        String retrieverMessage = retrieveDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 构建增强后的用户消息
        UserMessage enhancedMessage;
        String content = """
                用户问题：
                %s
                
                RAG检索内容：
                %s
                """.formatted(userQuestion.getText(), retrieverMessage == null ? "" : retrieverMessage);
        enhancedMessage = new UserMessage(content);


        // 构建最终消息列表
        List<Message> enhancedMessages = new ArrayList<>(messages);
        // 替换最后一个用户消息
        for (ListIterator<Message> it = enhancedMessages.listIterator(enhancedMessages.size()); it.hasPrevious(); ) {
            Message msg = it.previous();
            if (msg instanceof UserMessage) {
                it.set(enhancedMessage);
                break;
            }
        }
        log.info("RAGMessagesHook-beforeModel translation_record_id:{} enhancedMessages:{}",metadata.get("translation_record_id").toString(),enhancedMessages);
        return  CompletableFuture.completedFuture(Map.of("messages", enhancedMessages));
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

