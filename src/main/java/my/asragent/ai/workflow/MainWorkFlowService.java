package my.asragent.ai.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.graph.MainGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级主工作流服务。
 *
 * 约定：
 * - 每个 translationRecordId 对应一个 BlockingQueue
 * - 工作流通过 BlockingQueue 阻塞等待 completed 推送的文本
 * - 会话结束时向队列写入结束信号，解除阻塞并继续后续流程
 */
@Slf4j
@Service
public class MainWorkFlowService {
    /** 会话队列容量。 */
    private final int queueCapacity;
    /** 会话与阻塞队列映射。 */
    private final Map<String, BlockingQueue<String>> queueMap = new ConcurrentHashMap<>();
    /** 通知工作流停止等待的结束信号。 */
    public static final String FINISH_SIGNAL = "__WORKFLOW_FINISH__";

    public MainWorkFlowService(@Value("${workflow.queue.capacity:1024}") int queueCapacity) {
        this.queueCapacity = Math.max(1, queueCapacity);
    }

    public void startWorkFlow(BlockingQueue<String> blockingQueue, String userId, String translationRecordId) throws GraphStateException {
        HashMap<Object, Object> initMap = new HashMap<>();
        initMap.put("blockingQueue", blockingQueue);
        initMap.put("userId", userId);
        initMap.put("translationRecordId", translationRecordId);
        CompiledGraph compiledGraph = MainGraph.getGraph().compile();
        compiledGraph.stream(Map.of("init", initMap))
                .doOnNext(output -> {
                    if (output instanceof StreamingOutput<?> streamingOutput) {
                        if (streamingOutput.message() != null) {
                            // streaming output from streaming llm node
                            System.out.println("Streaming output from node " + streamingOutput.node() + ": " + streamingOutput.message().getText());
                        } else {
                            // output from normal node, investigate the state to get the node data
                            System.out.println("Output from node " + streamingOutput.node() + ": " + streamingOutput.state().data());
                        }
                    }
                })
                .blockLast();
    }

    /** 获取或创建会话队列。 */
    public BlockingQueue<String> getOrCreateQueue(String translationRecordId) {
        return queueMap.computeIfAbsent(translationRecordId, key -> new ArrayBlockingQueue<>(queueCapacity));
    }

    /** 向会话队列推送文本。 */
    public boolean offerText(String translationRecordId, String text) {
        if (translationRecordId == null || translationRecordId.isBlank() || text == null || text.isBlank()) {
            return false;
        }
        return getOrCreateQueue(translationRecordId).offer(text);
    }

    /** 通知会话工作流停止等待并继续后续流程。 */
    public void notifyFinish(String translationRecordId) {
        if (translationRecordId == null || translationRecordId.isBlank()) {
            return;
        }
        BlockingQueue<String> queue = queueMap.get(translationRecordId);
        if (queue == null) {
            return;
        }
        queue.offer(FINISH_SIGNAL);
    }

    /** 移除会话队列，释放内存。 */
    public void removeQueue(String translationRecordId) {
        if (translationRecordId == null || translationRecordId.isBlank()) {
            return;
        }
        queueMap.remove(translationRecordId);
    }
}

