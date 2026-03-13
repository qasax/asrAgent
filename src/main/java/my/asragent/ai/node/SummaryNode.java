package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.mybatisflex.core.query.QueryWrapper;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import my.asragent.utils.SpringContextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static my.asragent.ai.workflow.MainWorkFlowService.FINISH_SIGNAL;

/**
 * 总结类，总结转译内容并入库
 */
public class SummaryNode implements NodeAction {


    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map initMap = (HashMap<String, Object>) state.value("init").get();
        ReactAgent summaryAgent = (ReactAgent) SpringContextUtil.getBean("summaryAgent");
        TranslationResultService translationResultService = SpringContextUtil.getBean(TranslationResultService.class);
        Map<String, Object> returnMap = new HashMap<>();

        BlockingQueue blockingQueue = (BlockingQueue) initMap.get("blockingQueue");
        try {
            Boolean isStop = false;
            while (!isStop) {
                Long recordId = Long.valueOf(initMap.get("translationRecordId").toString());
                QueryWrapper queryWrapper = new QueryWrapper();
                queryWrapper.eq("id", recordId);
                String stageText = blockingQueue.take().toString();
                if (stageText.equals(FINISH_SIGNAL)) {
                    returnMap.put("summary", translationResultService.getOne(queryWrapper).getSummaryText());
                    isStop = true;
                    continue;
                }
                //获取全文，进行阶段性总结
                AssistantMessage assistantMessage = summaryAgent.call(new UserMessage(stageText));
                //追加总结入库
                TranslationResult translationResult = translationResultService.getOne(queryWrapper);
                translationResult.setSummaryText(translationResult.getSummaryText() + assistantMessage.getText());
                QueryWrapper updateWrapper = new QueryWrapper();
                updateWrapper.eq("id", translationResult.getId());
                translationResultService.update(translationResult, updateWrapper);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return returnMap;
    }
}

