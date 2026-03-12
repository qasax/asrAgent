package my.asragent.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "通用删除请求")
public class DeleteRequest implements Serializable {

    @Schema(description = "要删除的记录 ID", required = true, example = "1")
    private Long id;

    private static final long serialVersionUID = 1L;
}