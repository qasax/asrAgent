package my.asragent.model.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户登录请求")
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    @Schema(description = "邮箱", required = true, example = "zhangsan@example.com")
    private String email;

    @Schema(description = "密码", required = true, example = "12345678")
    private String password;
}
