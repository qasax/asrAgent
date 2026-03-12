package my.asragent.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用分页请求")
public class PageRequest {

    @Schema(description = "当前页号（从 1 开始）", example = "1")
    private int pageNum = 1;

    @Schema(description = "每页记录数", example = "10")
    private int pageSize = 10;

    @Schema(description = "排序字段", example = "createTime")
    private String sortField;

    @Schema(description = "排序顺序：ascend（升序）/ descend（降序），默认降序", example = "descend")
    private String sortOrder = "descend";
}