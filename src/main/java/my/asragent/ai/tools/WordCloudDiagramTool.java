package my.asragent.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WordCloudDiagramTool {

    private static final int MAX_WORD_COUNT = 120;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z][A-Za-z0-9_-]{1,}|\\d+");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9_-]{1,32})\\s*[:：=]\\s*(\\d{1,4})");

    private static final Set<String> STOP_WORDS = new HashSet<>(Set.of(
            "的", "了", "和", "是", "在", "就", "都", "而", "及", "与", "或", "对", "中", "上", "下", "把", "被", "将", "这", "那", "一个", "我们", "你们", "他们", "它们", "以及", "进行", "如果", "因为", "所以", "需要", "可以", "通过", "已经", "还是", "这个", "那个", "一些", "一种", "例如", "主要", "相关", "非常", "还有", "然后", "同时",
            "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with", "as", "at", "by", "is", "are", "was", "were", "be", "been", "being", "it", "this", "that", "these", "those", "we", "you", "they", "he", "she", "i", "from", "into", "over", "under", "about", "also", "can", "could", "should", "would"
    ));

    @Resource
    private MinioFileUploadUtil minioFileUploadUtil;

    @Tool(description = "根据 AI 回复内容生成词云图图片，并返回可访问链接")
    public String generateWordCloudDiagram(@ToolParam(description = "AI 回复文本，支持纯文本或'词语:权重'格式") String aiReply) {
        if (StrUtil.isBlank(aiReply)) {
            return "";
        }
        File outputFile = null;
        try {
            List<WeightedWord> words = buildWeightedWords(aiReply);
            if (words.isEmpty()) {
                return "";
            }
            outputFile = convertToWordCloudPng(words);
            String objectName = String.format("diagram/wordcloud/%s.png",
                    UUID.randomUUID().toString().replace("-", ""));
            String imageUrl = minioFileUploadUtil.uploadFile(outputFile, objectName, "image/png");
            return StrUtil.blankToDefault(imageUrl, "");
        } catch (Exception e) {
            log.error("生成词云图失败: {}", e.getMessage(), e);
            return "";
        } finally {
            if (outputFile != null) {
                FileUtil.del(outputFile);
            }
        }
    }

    private File convertToWordCloudPng(List<WeightedWord> words) {
        File inputJsonFile = FileUtil.createTempFile("wordcloud_input_", ".json", true);
        File scriptFile = FileUtil.createTempFile("wordcloud_render_", ".cjs", true);
        File outputPngFile = FileUtil.createTempFile("wordcloud_output_", ".png", true);
        try {
            FileUtil.writeUtf8String(JSONUtil.toJsonStr(words), inputJsonFile);
            FileUtil.writeUtf8String(buildNodeRenderScript(), scriptFile);
            String command = SystemUtil.getOsInfo().isWindows() ? "node.exe" : "node";
            String cmdLine = String.format("%s \"%s\" \"%s\" \"%s\"",
                    command,
                    scriptFile.getAbsolutePath(),
                    inputJsonFile.getAbsolutePath(),
                    outputPngFile.getAbsolutePath());
            String result = RuntimeUtil.execForStr(cmdLine);
            if (!outputPngFile.exists() || outputPngFile.length() == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "d3-cloud 渲染失败: " + result);
            }
            return outputPngFile;
        } finally {
            FileUtil.del(inputJsonFile);
            FileUtil.del(scriptFile);
        }
    }

    private List<WeightedWord> buildWeightedWords(String text) {
        Map<String, Integer> weightedMap = parseWeightedWords(text);
        if (weightedMap.isEmpty()) {
            weightedMap = tokenizeAndCount(text);
        }
        return weightedMap.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(MAX_WORD_COUNT)
                .map(entry -> new WeightedWord(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<String, Integer> parseWeightedWords(String text) {
        Map<String, Integer> weightedMap = new HashMap<>();
        Matcher matcher = WEIGHT_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawWord = matcher.group(1);
            String rawWeight = matcher.group(2);
            String word = normalizeToken(rawWord);
            if (!isValidToken(word)) {
                continue;
            }
            int weight = Integer.parseInt(rawWeight);
            if (weight <= 0) {
                continue;
            }
            weightedMap.put(word, Math.min(weight, 1000));
        }
        return weightedMap.size() >= 3 ? weightedMap : new HashMap<>();
    }

    private Map<String, Integer> tokenizeAndCount(String text) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        String normalizedText = text.replaceAll("[\\r\\n\\t,，。！？；;、()（）\\[\\]{}<>《》\"“”'`|]", " ");
        Matcher matcher = TOKEN_PATTERN.matcher(normalizedText);
        while (matcher.find()) {
            String word = normalizeToken(matcher.group());
            if (!isValidToken(word)) {
                continue;
            }
            frequencyMap.merge(word, 1, Integer::sum);
        }
        return frequencyMap;
    }

    private String normalizeToken(String token) {
        if (StrUtil.isBlank(token)) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.chars().allMatch(ch -> ch < 128)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    private boolean isValidToken(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        if (token.length() > 24) {
            return false;
        }
        if (STOP_WORDS.contains(token)) {
            return false;
        }
        return !(token.length() == 1 && Character.isDigit(token.charAt(0)));
    }

    private String buildNodeRenderScript() {
        return """
            const fs = require('fs');
            const cloud = require('d3-cloud');
            const { createCanvas } = require('canvas');

            const inputFile = process.argv[2];
            const outputFile = process.argv[3];

            const WIDTH = 1280;
            const HEIGHT = 720;

            const COLORS = [
              '#1f77b4',
              '#2ca02c',
              '#d62728',
              '#ff7f0e',
              '#17becf',
              '#8c564b'
            ];

            const data = JSON.parse(fs.readFileSync(inputFile, 'utf-8'));

            if (!Array.isArray(data) || data.length === 0) {
              throw new Error('No words to render');
            }

            const weights = data.map(w => Number(w.weight || 1));
            const minWeight = Math.min(...weights);
            const maxWeight = Math.max(...weights);

            function sizeOf(weight) {
              if (maxWeight === minWeight) {
                return 48;
              }
              return Math.round(
                18 + ((weight - minWeight) * (92 - 18)) / (maxWeight - minWeight)
              );
            }

            const words = data.map(item => ({
              text: String(item.text),
              size: sizeOf(Number(item.weight || 1))
            }));

            const layout = cloud()
              .size([WIDTH, HEIGHT])
              .canvas(() => createCanvas(WIDTH, HEIGHT))
              .words(words)
              .padding(3)
              .rotate(() => (Math.random() > 0.82 ? 90 : 0))
              .font('sans-serif')
              .fontSize(d => d.size)
              .on('end', draw);

            layout.start();

            function draw(layoutWords) {

              const canvas = createCanvas(WIDTH, HEIGHT);
              const ctx = canvas.getContext('2d');

              ctx.fillStyle = '#ffffff';
              ctx.fillRect(0, 0, WIDTH, HEIGHT);

              layoutWords.forEach((word, index) => {

                ctx.save();

                ctx.translate(
                  word.x + WIDTH / 2,
                  word.y + HEIGHT / 2
                );

                ctx.rotate((word.rotate || 0) * Math.PI / 180);

                ctx.fillStyle = COLORS[index % COLORS.length];

                ctx.font = `${word.size}px sans-serif`;

                ctx.fillText(word.text, 0, 0);

                ctx.restore();

              });

              const buffer = canvas.toBuffer('image/png');

              fs.writeFileSync(outputFile, buffer);

            }
            """;
    }

    private record WeightedWord(String text, int weight) {
    }
}
