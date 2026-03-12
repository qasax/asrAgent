package my.asragent.model.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import my.asragent.common.PageRequest;

import java.io.Serializable;
import java.math.BigInteger;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "用户查询请求（支持分页和多条件筛选）")
public class UserQueryRequest extends PageRequest implements Serializable {

    @Schema(description = "用户 ID（精确匹配）", example = "1")
    private BigInteger id;

    @Schema(description = "用户名（模糊匹配）", example = "张")
    private String username;

    @Schema(description = "邮箱（模糊匹配）", example = "@example.com")
    private String email;

    @Schema(description = "账号状态：1启用/0禁用", example = "1")
    private Integer status;

    @Schema(description = "用户角色筛选：user / admin", example = "user")
    private String userRole;

    private static final long serialVersionUID = 1L;
}
