package my.asragent.ai.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.chatModel.DispChatModel;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.tools.MermaidDiagramTool;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import my.asragent.utils.MinioFileUploadUtil;
import my.asragent.utils.SpringContextUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class KeyWordNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) {
        MinioFileUploadUtil minioFileUploadUtil = SpringContextUtil.getBean(MinioFileUploadUtil.class);
        ImageGenerationDecision imageGenerationDecision = (ImageGenerationDecision) state.value("imgPlan").get();
        if (imageGenerationDecision.getKeywordCard().isGenerate()) {
            Map initMap = (HashMap<String, Object>) state.value("init").get();
            String translationRecordId = initMap.get("translationRecordId").toString();
            log.info("进入关键词图生成阶段 TranslationId:{}", translationRecordId);
            ImageSynthesisResult imageSynthesisResult = call(imageGenerationDecision.getKeywordCard().getPrompt());
            List<Map<String, String>> results = imageSynthesisResult.getOutput().getResults();
            String uploadedFileUrl = "";
            for (Map<String, String> result : results) {
                String url = result.get("url");
                // 下载到临时文件
                File tempFile = FileUtil.createTempFile("image_", ".png", true);

                // HttpUtil.downloadFile 会直接把文件下载到指定 File
                HttpUtil.downloadFile(url, tempFile);
                uploadedFileUrl = minioFileUploadUtil.uploadFile(tempFile, translationRecordId + "keyWordImg");
            }
            boolean updated = ImageResultNodeSupport.updateImageField(Long.valueOf(translationRecordId), uploadedFileUrl, "keyWord");
            log.info("关键词图片生成结束 url:{} TranslationId:{} updated:{}", uploadedFileUrl, translationRecordId, updated);
            return Map.of();
        } else {
            log.info("跳过关键词图片，计划不需要关键词图片");
            return Map.of();
        }
    }

    private ImageSynthesisResult call(String prompt) {
        prompt = "请根据以下内容生成一个“关键词卡片信息图”。\n" +
                "\n" +
                "设计要求：\n" +
                "\n" +
                "1. 风格：知识图谱风格、专业、正式\n" +
                "2. 背景：浅色渐变\n" +
                "3. 标题在顶部\n" +
                "4. 中间展示 5-8 个关键词\n" +
                "5. 每个关键词包含：\n" +
                "   - 关键词\n" +
                "   - 一句话解释\n" +
                "6. 关键词使用卡片式布局\n" +
                "7. 字体清晰，适合阅读\n" +
                "8. 整体风格简洁\n" +
                "内容:" + prompt;
        DispChatModel dispChatModel = SpringContextUtil.getBean(DispChatModel.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("negative_prompt", " ");
        ImageSynthesisParam param =
                ImageSynthesisParam.builder()
                        .apiKey(dispChatModel.getApiKey())
                        // 当前仅qwen-image-plus、qwen-image模型支持异步接口
                        .model(dispChatModel.getImageModel())
                        .prompt(prompt)
                        .n(1)
                        .size("1664*928")
                        .parameters(parameters)
                        .build();

        ImageSynthesis imageSynthesis = new ImageSynthesis();
        ImageSynthesisResult result = null;
        try {
            log.info("---同步调用，请等待任务执行----");
            return imageSynthesis.call(param);
        } catch (ApiException | NoApiKeyException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
