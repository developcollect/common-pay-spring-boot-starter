package com.developcollect.commonpay.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Slf4j
@ConditionalOnExpression("${zak.pay.resource-endpoint.enabled:true}")
@Configuration
public class CommonPayWebMvcConfig implements WebMvcConfigurer {



    /**
     * 收款二维码
     *
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = CommonPaySpringUtil.appHome();
        registry.addResourceHandler("/cPay/r/wxpay/**").addResourceLocations("file:" + path + "/cPay/wxpay/");
        registry.addResourceHandler("/cPay/r/alipay/**").addResourceLocations("file:" + path + "/cPay/alipay/");
    }


}
