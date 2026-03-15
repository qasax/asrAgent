package my.asragent.ai.node;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import my.asragent.utils.SpringContextUtil;

@Slf4j
public final class ImageResultNodeSupport {

    private ImageResultNodeSupport() {
    }

    public static boolean updateImageField(Long translationRecordId, String imageUrl, String imageType) {
        if (translationRecordId == null || StrUtil.isBlank(imageUrl)) {
            return false;
        }
        TranslationResultService translationResultService = SpringContextUtil.getBean(TranslationResultService.class);
        TranslationResult translationResult = translationResultService.getById(translationRecordId);
        if (translationResult == null) {
            log.warn("图片结果入库跳过，未找到 translation 记录 id={}", translationRecordId);
            return false;
        }
        if ("wordCloud".equals(imageType)) {
            translationResult.setExtend1(imageUrl);
        } else if("mindMap".equals(imageType)) {
            translationResult.setImgUrl(imageUrl);
        }else{
            translationResult.setExtend2(imageUrl);
        }
        return translationResultService.updateById(translationResult);
    }
}
