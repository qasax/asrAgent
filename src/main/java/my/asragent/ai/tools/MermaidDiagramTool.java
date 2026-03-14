package my.asragent.ai.tools;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.asragent.exception.BusinessException;
import my.asragent.exception.ErrorCode;
import my.asragent.utils.MinioFileUploadUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class MermaidDiagramTool {

    @Resource
    private MinioFileUploadUtil minioFileUploadUtil;

    @Tool(description ="将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public String generateMermaidDiagram(@ToolParam(description = "Mermaid 图表代码") String mermaidCode) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new String();
        }
        try {
            // 转换为SVG图片
            File diagramFile = convertMermaidToSvg(mermaidCode);
            String objectName = String.format("diagram/mermaid/%s.svg",
                    UUID.randomUUID().toString().replace("-", ""));
            String imageUrl = minioFileUploadUtil.uploadFile(diagramFile, objectName, "image/svg+xml");
            // 清理临时文件
            FileUtil.del(diagramFile);
            if (StrUtil.isNotBlank(imageUrl)) {
                return imageUrl;
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        }
        return new String();
    }

    /**
     * 将Mermaid代码转换为SVG图片
     */
    private File convertMermaidToSvg(String mermaidCode) {
        // 创建临时输入文件
        File tempInputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, tempInputFile);
        // 创建临时输出文件
        File tempOutputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        // 根据操作系统选择命令
        String command = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
        // 构建命令
        String cmdLine = String.format("%s -i %s -o %s -b transparent",
                command,
                tempInputFile.getAbsolutePath(),
                tempOutputFile.getAbsolutePath()
        );
        // 执行命令
        RuntimeUtil.execForStr(cmdLine);
        // 检查输出文件
        if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
        }
        // 清理输入文件，保留输出文件供上传使用
        FileUtil.del(tempInputFile);
        return tempOutputFile;
    }
}
