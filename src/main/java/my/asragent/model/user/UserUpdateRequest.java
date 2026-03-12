package my.asragent.model.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;

@Data
@Schema(description = "更新用户请求（管理员专用）")
public class UserUpdateRequest implements Serializable {

    @Schema(description = "用户 ID", required = true, example = "1")
    private BigInteger id;

    @Schema(description = "用户名", example = "李四")
    private String username;

    @Schema(description = "邮箱", example = "lisi@example.com")
    private String email;

    @Schema(description = "账号状态：1启用/0禁用", example = "1")
    private Integer status;

    @Schema(description = "用户角色：user / admin", example = "user")
    private String userRole;

    private static final long serialVersionUID = 1L;
}
