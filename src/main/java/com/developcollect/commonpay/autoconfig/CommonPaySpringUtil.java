package com.developcollect.commonpay.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * spring 工具类
 * @author zak
 * @since 1.0.0
 */
@Slf4j
@Component
class CommonPaySpringUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (applicationContext.getParent() == CommonPaySpringUtil.applicationContext
                || CommonPaySpringUtil.applicationContext == null) {
            CommonPaySpringUtil.applicationContext = applicationContext;
        }
    }



    /**
     * 获取当前运行项目的路径
     *
     * @return 当前运行项目的路径
     * @author zak
     * @since 1.0.0
     */
    public static String appHome() {
        ApplicationHome home = new ApplicationHome();
        return home.getDir().getAbsolutePath();
    }


    /**
     * 发布事件
     *
     * @param event 事件对象
     * @author zak
     * @since 1.0.0
     */
    public static void publishEvent(ApplicationEvent event) {
        getApplicationContext().publishEvent(event);
    }


    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static <T> T getBean(Class<T> requiredType) throws BeansException {
        return getApplicationContext().getBean(requiredType);
    }

    public static <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
        return getApplicationContext().getBeansOfType(type);
    }

    public static Object getBean(String name) throws BeansException {
        return getApplicationContext().getBean(name);
    }

    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        Map<String, Object> beansWithAnnotation = getApplicationContext().getBeansWithAnnotation(annotationType);
        return beansWithAnnotation;
    }
}
