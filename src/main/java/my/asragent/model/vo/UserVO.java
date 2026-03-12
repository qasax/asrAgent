package my.asragent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户信息视图（脱敏后的公开信息）")
public class UserVO implements Serializable {

    @Schema(description = "用户 ID", example = "1")
    private BigInteger id;

    @Schema(description = "用户名", example = "张三")
    private String username;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "用户角色：user / admin", example = "user")
    private String userRole;

    @Schema(description = "账号状态：1启用/0禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginAt;

    private static final long serialVersionUID = 1L;
}
