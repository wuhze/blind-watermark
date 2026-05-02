package com.blindwatermark;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring应用上下文持有者
 * 提供静态方法让非Spring管理的类（如JavaFX控制器）获取Spring Bean
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    /**
     * Spring容器初始化时自动调用，注入ApplicationContext
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.context = applicationContext;
    }

    /**
     * 根据类型获取Spring Bean
     *
     * @param clazz Bean类型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * 根据名称获取Spring Bean
     *
     * @param name Bean名称
     * @return Bean实例
     */
    public static Object getBean(String name) {
        return context.getBean(name);
    }

    /**
     * 获取Spring应用上下文
     */
    public static ApplicationContext getContext() {
        return context;
    }
}
