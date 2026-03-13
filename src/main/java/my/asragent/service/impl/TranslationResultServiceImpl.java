package my.asragent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import my.asragent.entity.TranslationResult;
import my.asragent.mapper.TranslationResultMapper;
import my.asragent.service.TranslationResultService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author zhangfajin
 */
@Service
public class TranslationResultServiceImpl extends ServiceImpl<TranslationResultMapper, TranslationResult>  implements TranslationResultService{

}
