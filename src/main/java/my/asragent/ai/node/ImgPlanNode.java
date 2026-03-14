package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.fastjson.JSONObject;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.utils.SpringContextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ImgPlanNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map initMap = (HashMap<String, Object>) state.value("init").get();
        String summary = (String) state.value("summary").get();
        ReactAgent summaryAgent = (ReactAgent) SpringContextUtil.getBean("imgPlanAgent");
        AssistantMessage assistantMessage = summaryAgent.call(new UserMessage(summary));
        ImageGenerationDecision imageGenerationDecision = (ImageGenerationDecision) JSONObject.parse(assistantMessage.getText());

        return Map.of("imgPlan",imageGenerationDecision);
    }
}
