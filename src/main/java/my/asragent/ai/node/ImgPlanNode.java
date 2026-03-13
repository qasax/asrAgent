package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ImgPlanNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map initMap = (HashMap<String, Object>) state.value("init").get();
        String summary = (String) state.value("summary").get();
        return Map.of();
    }
}
