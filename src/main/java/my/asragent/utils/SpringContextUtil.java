package my.asragent.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 用于在静态上下文中访问 Bean 的 Spring 上下文工具。
 *
 * 尽量使用构造器注入；仅在必须静态访问时使用。
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.applicationContext = applicationContext;
    }

    /** 按类型获取 Bean。 */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    /** 按名称获取 Bean。 */
    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }

    /** 按名称和类型获取 Bean。 */
    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }
}
