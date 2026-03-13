package my.asragent.ai.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import my.asragent.ai.node.ImgPlanNode;
import my.asragent.ai.node.SummaryNode;
import my.asragent.ai.state.MainState;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public class MainGraph {
    public static StateGraph getGraph() throws GraphStateException {
        StateGraph stateGraph = new StateGraph(MainState.createKeyStrategyFactory());
        stateGraph.addNode("summary_node",node_async(new SummaryNode()));
        stateGraph.addNode("imgPlan_node",node_async(new ImgPlanNode()));
        stateGraph.addEdge(StateGraph.START,"summary_node");
        stateGraph.addEdge("summary_node","imgPlan_node");
        stateGraph.addEdge("imgPlan_node",StateGraph.END);
    return stateGraph;
    }
}
