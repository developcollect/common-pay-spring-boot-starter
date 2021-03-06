package com.developcollect.commonpay.autoconfig;

import com.developcollect.dcinfra.utils.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Slf4j
@ConditionalOnExpression("${develop-collect.pay.resource-endpoint.enabled:true}")
@Configuration
public class CommonPayWebMvcConfig implements WebMvcConfigurer {



    /**
     * 静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = SpringUtil.appHome();
        registry.addResourceHandler("/cPay/r/wxpay/**").addResourceLocations("file:" + path + "/cPay/wxpay/");
        registry.addResourceHandler("/cPay/r/alipay/**").addResourceLocations("file:" + path + "/cPay/alipay/");
    }


}
