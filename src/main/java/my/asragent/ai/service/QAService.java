package my.asragent.ai.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
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

    public Flux<String> getAnswer(String translationRecordId, String userMessage) {
        try {
            Flux<Message> messageFlux = qaAgent.streamMessages(new UserMessage(userMessage));
            return messageFlux.map(message -> message.getText());
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        }

}
