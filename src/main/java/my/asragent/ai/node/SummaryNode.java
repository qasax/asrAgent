package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.workflow.MainWorkFlowService;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import my.asragent.utils.SpringContextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static my.asragent.ai.workflow.MainWorkFlowService.FINISH_SIGNAL;

/**
 * 总结类，总结转译内容并入库
 */
@Slf4j
public class SummaryNode implements NodeAction {

    // 可配置的批处理大小
    private static final int BATCH_SIZE = 5;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map initMap = (HashMap<String, Object>) state.value("init").get();
        String translationRecordId = initMap.get("translationRecordId").toString();
        log.info("进入阶段总结节点TranslationId:{}", translationRecordId);

        ReactAgent summaryAgent = (ReactAgent) SpringContextUtil.getBean("summaryAgent");
        ReactAgent finalSummaryAgent = (ReactAgent) SpringContextUtil.getBean("finalSummaryAgent");
        TranslationResultService translationResultService = SpringContextUtil.getBean(TranslationResultService.class);
        MainWorkFlowService mainWorkFlowService = SpringContextUtil.getBean(MainWorkFlowService.class);

        BlockingQueue<String> blockingQueue = mainWorkFlowService.getOrCreateQueue(translationRecordId);
        Long recordId = Long.valueOf(translationRecordId);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("id", recordId);

        Map<String, Object> returnMap = new HashMap<>();

        try {
            // 用于缓存批量消息
            List<String> batchBuffer = new ArrayList<>();

            while (true) {
                String stageText = blockingQueue.take();

                if (FINISH_SIGNAL.equals(stageText)) {
                    // 收到结束信号，处理剩余所有消息
                    if (!batchBuffer.isEmpty()) {
                        processBatch(summaryAgent, translationResultService, queryWrapper, batchBuffer);
                        batchBuffer.clear();
                    }
                    TranslationResult latest = translationResultService.getOne(queryWrapper);
                    returnMap.put("summary", latest == null ? "" : StrUtil.nullToDefault(latest.getSummaryText(), ""));
                    break;
                }

                batchBuffer.add(stageText);

                // 如果达到批量处理大小，执行批处理
                if (batchBuffer.size() >= BATCH_SIZE) {
                    processBatch(summaryAgent, translationResultService, queryWrapper, batchBuffer);
                    batchBuffer.clear();
                }
            }
            //进行最终总结
            AssistantMessage assistantMessage = finalSummaryAgent.call(new UserMessage(returnMap.get("summary").toString()));
            TranslationResult finalResult = new TranslationResult();
            finalResult.setId(recordId);
            finalResult.setSummaryText(assistantMessage.getText());
            //更新总结入库
            translationResultService.updateById(finalResult);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("退出阶段总结节点:{}", translationRecordId);
        return returnMap;
    }

    /**
     * 批量处理消息并更新数据库
     */
    private void processBatch(ReactAgent summaryAgent,
                              TranslationResultService translationResultService,
                              QueryWrapper queryWrapper,
                              List<String> batchMessages) throws GraphRunnerException {

        // 合并批量消息为一段文本
        String combinedText = String.join("\n", batchMessages);

        AssistantMessage assistantMessage = summaryAgent.call(new UserMessage(combinedText));
        log.info("批量总结入库：{}", assistantMessage);

        TranslationResult translationResult = translationResultService.getOne(queryWrapper);
        if (translationResult == null) {
            log.warn("阶段总结入库跳过，未找到 translation 记录 queryWrapper={}", queryWrapper);
            return;
        }

        String oldSummary = StrUtil.nullToDefault(translationResult.getSummaryText(), "");
        String delta = assistantMessage == null ? "" : StrUtil.nullToDefault(assistantMessage.getText(), "");
        translationResult.setSummaryText(oldSummary + delta);

        QueryWrapper updateWrapper = new QueryWrapper();
        updateWrapper.eq("id", translationResult.getId());
        translationResultService.update(translationResult, updateWrapper);
    }
}

