package my.asragent.model.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    @Schema(description = "用户名", required = true, example = "张三")
    private String username;

    @Schema(description = "邮箱", required = true, example = "zhangsan@example.com")
    private String email;

    @Schema(description = "密码（至少 8 位）", required = true, example = "12345678")
    private String password;

    @Schema(description = "确认密码（需与密码一致）", required = true, example = "12345678")
    private String checkPassword;
}
