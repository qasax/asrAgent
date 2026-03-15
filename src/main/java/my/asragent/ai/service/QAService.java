package my.asragent.ai.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import my.asragent.service.UserService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;

@Service
public class QAService {
    @Resource
    private ReactAgent qaAgent;
    @Resource
    private UserService userService;

    public Flux<String> getAnswer(String translationRecordId, String userMessage, HttpServletRequest request) {
        try {
            RunnableConfig runnableConfig = RunnableConfig.builder().addMetadata("translation_record_id", translationRecordId)
                    .addMetadata("user_id", userService.getLoginUser(request).getId())
                    .threadId(String.valueOf(Thread.currentThread().threadId()))
                    .build();
            Flux<Message> messageFlux = qaAgent.streamMessages(new UserMessage(userMessage),runnableConfig);
            return messageFlux.map(message -> message.getText());
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        }

}
