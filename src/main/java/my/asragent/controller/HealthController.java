package my.asragent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "服务健康与存活检查")
/** 用于探针和冒烟检查的轻量存活端点。 */
public class HealthController {

    @GetMapping
    @Operation(summary = "健康检查", description = "返回服务健康状态")
    /** 返回固定的健康响应。 */
    public String healthCheck() {
        return "ok";
    }
}
