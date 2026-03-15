package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.workflow.MainWorkFlowService;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import my.asragent.utils.SpringContextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static my.asragent.ai.workflow.MainWorkFlowService.FINISH_SIGNAL;

/**
 * 总结类，总结转译内容并入库
 */
@Slf4j
public class SummaryNode implements NodeAction {


    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map initMap = (HashMap<String, Object>) state.value("init").get();
        String translationRecordId = initMap.get("translationRecordId").toString();
        log.info("进入阶段总结节点TranslationId:{}",translationRecordId);
        ReactAgent summaryAgent = (ReactAgent) SpringContextUtil.getBean("summaryAgent");
        TranslationResultService translationResultService = SpringContextUtil.getBean(TranslationResultService.class);
        Map<String, Object> returnMap = new HashMap<>();
        //state无法存blockingQueue，丢失了原有的对象
        //BlockingQueue blockingQueue = (BlockingQueue) initMap.get("blockingQueue");
        //使用bean获取
        MainWorkFlowService mainWorkFlowService = SpringContextUtil.getBean(MainWorkFlowService.class);
        BlockingQueue<String> blockingQueue = mainWorkFlowService.getOrCreateQueue(translationRecordId);
        Long recordId = Long.valueOf(translationRecordId);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("id", recordId);
        try {
            Boolean isStop = false;
            while (!isStop) {
                String stageText = blockingQueue.take().toString();
                if (stageText.equals(FINISH_SIGNAL)) {
                    TranslationResult latest = translationResultService.getOne(queryWrapper);
                    returnMap.put("summary", latest == null ? "" : StrUtil.nullToDefault(latest.getSummaryText(), ""));
                    isStop = true;
                    break;
                }
                //获取全文，进行阶段性总结
                AssistantMessage assistantMessage = summaryAgent.call(new UserMessage(stageText));
                log.info("总结入库：{}",assistantMessage.toString());
                //追加总结入库
                TranslationResult translationResult = translationResultService.getOne(queryWrapper);
                if (translationResult == null) {
                    log.warn("阶段总结入库跳过，未找到 translation 记录 id={}", recordId);
                    continue;
                }
                String oldSummary = StrUtil.nullToDefault(translationResult.getSummaryText(), "");
                String delta = assistantMessage == null ? "" : StrUtil.nullToDefault(assistantMessage.getText(), "");
                translationResult.setSummaryText(oldSummary + delta);
                QueryWrapper updateWrapper = new QueryWrapper();
                updateWrapper.eq("id", translationResult.getId());
                translationResultService.update(translationResult, updateWrapper);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("退出阶段总结节点:{}",translationRecordId);
        return returnMap;
    }
}

