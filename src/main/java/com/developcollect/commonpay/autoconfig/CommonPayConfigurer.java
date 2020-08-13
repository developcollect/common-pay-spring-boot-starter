package com.developcollect.commonpay.autoconfig;


import com.developcollect.commonpay.config.GlobalConfig;

/**
 * 支付配置器
 *
 * @author zak
 * @since 1.0.0
 */
public interface CommonPayConfigurer {


    void config(GlobalConfig globalConfig);

}
