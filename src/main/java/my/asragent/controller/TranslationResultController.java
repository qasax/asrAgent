package my.asragent.controller;

import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;

/**
 *  控制层。
 *
 * @author zhangfajin
 */
@RestController
@RequestMapping("/translationResult")
@Tag(name = "转译结果", description = "转译结果的增删改查接口")
public class TranslationResultController {

    @Autowired
    private TranslationResultService translationResultService;

    /**
     * 保存。
     *
     * @param translationResult 
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    @Operation(summary = "新增转译结果", description = "创建一条转译结果记录")
    public boolean save(@RequestBody TranslationResult translationResult) {
        return translationResultService.save(translationResult);
    }

    /**
     * 根据主键删除。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    @Operation(summary = "删除转译结果", description = "根据主键删除转译结果")
    public boolean remove(@Parameter(description = "记录ID", required = true) @PathVariable BigInteger id) {
        return translationResultService.removeById(id);
    }

    /**
     * 根据主键更新。
     *
     * @param translationResult 
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    @Operation(summary = "更新转译结果", description = "根据主键更新转译结果")
    public boolean update(@RequestBody TranslationResult translationResult) {
        return translationResultService.updateById(translationResult);
    }

    /**
     * 查询所有。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    @Operation(summary = "查询全部转译结果", description = "查询所有转译结果记录")
    public List<TranslationResult> list() {
        return translationResultService.list();
    }

    /**
     * 根据主键获取。
     *
     * @param id 主键
     * @return 详情
     */
    @GetMapping("getInfo/{id}")
    @Operation(summary = "获取转译结果详情", description = "根据主键获取转译结果详情")
    public TranslationResult getInfo(@Parameter(description = "记录ID", required = true) @PathVariable BigInteger id) {
        return translationResultService.getById(id);
    }

    /**
     * 分页查询。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    @Operation(summary = "分页查询转译结果", description = "分页查询转译结果列表")
    public Page<TranslationResult> page(Page<TranslationResult> page) {
        return translationResultService.page(page);
    }

}
