package my.asragent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
/** 定义用于 LLM 后台任务的异步执行器 Bean。 */
public class AsyncConfig {
    /**
     * 为翻译/摘要/问答任务提供专用固定线程池。
     *
     * 有界线程池有助于在并发下控制内存和 CPU 使用。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService llmExecutorService() {
        return Executors.newFixedThreadPool(4);
    }
}
