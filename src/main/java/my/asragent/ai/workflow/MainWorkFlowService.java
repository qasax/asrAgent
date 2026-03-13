package my.asragent.ai.workflow;


/**
 * (转译线程)
 * 实时转录 + 实时翻译
 * │
 * ▼
 * 增量入向量库 + 队列推送到主工作流
 * │
 * ▼
 * ArrayBlockingQueue (或 LinkedBlockingQueue)
 * └── 阻塞等待主工作流处理
 * │
 * ▼
 * 节点1：上下文累积 Memory + 滑动窗口增量总结 → 阶段入库 → SSE 推送阶段总结
 * │
 * ▼
 * 节点2：对全部总结分析文本内容 → 决定生成可视化类型
 * │
 * ▼
 * 节点3：调用 Tools 异步生成可视化内容
 * │
 * ▼
 * 节点4：统一入库持久化保存（原始转录/总结/可视化元数据）
 */
public class MainWorkFlowService {


}
