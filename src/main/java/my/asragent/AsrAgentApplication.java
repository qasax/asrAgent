package my.asragent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/** Spring Boot 应用入口。 */
@MapperScan("my.asragent.mapper")
public class AsrAgentApplication {

    /** 启动 ASR 代理服务。 */
    public static void main(String[] args) {
        SpringApplication.run(AsrAgentApplication.class, args);
    }

}
