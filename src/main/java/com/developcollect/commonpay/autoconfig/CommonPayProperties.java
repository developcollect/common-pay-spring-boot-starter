package com.developcollect.commonpay.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 全局支付属性
 *
 * @author zak
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "zak.pay")
public class CommonPayProperties implements EnvironmentAware {

    @Override
    public void setEnvironment(Environment environment) {
        port = Optional.ofNullable(environment.getProperty("server.port", Integer.class)).orElse(8080);
        contextPath = Optional.ofNullable(environment.getProperty("server.servlet.context-path")).orElse("");
    }

    /**
     * 主动查询通知间隔时间，单位：ms
     */
    private long queryNoticeDelay = 600000;

    /**
     * 域名
     */
    private String domain;

    /**
     * 端口
     */
    private int port;

    private String contextPath;

    /**
     * 是否启用https
     */
    private boolean ssl = false;

    /**
     * 通知端点设置
     */
    @NestedConfigurationProperty
    private EndpointProperties notifyEndpoint = new EndpointProperties();

    /**
     * 静态资源端点设置
     */
    @NestedConfigurationProperty
    private EndpointProperties resourceEndpoint = new EndpointProperties();

    @NestedConfigurationProperty
    private AliPayProperties alipay = new AliPayProperties();

    @NestedConfigurationProperty
    private WxPayProperties wxpay = new WxPayProperties();


    public void setContextPath(String contextPath) {
        this.contextPath = cleanContextPath(contextPath);
    }

    private String cleanContextPath(String contextPath) {
        String candidate = StringUtils.trimWhitespace(contextPath);
        if (StringUtils.hasText(candidate) && candidate.endsWith("/")) {
            return candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }


    public String getUrlPrefix() {
        return String.format("%s://%s:%s%s",
                this.isSsl() ? "https" : "http",
                this.getDomain(),
                this.getPort(),
                this.getContextPath()
        );
    }

}

/**
 * 支付宝支付属性
 *
 * @author zak
 * @since 1.0.0
 */
@Data
class AliPayProperties {

    /**
     * 是否使用沙箱环境
     */
    private boolean useSandbox = false;

    /**
     * 支付宝appid
     */
    private String appid;

    /**
     * 私钥
     */
    private String privateKey;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 编码集，支持 GBK/UTF-8
     */
    private String charset = "UTF-8";

    /**
     * 商户生成签名字符串所使用的签名算法类型，目前支持 RSA2 和 RSA，推荐使用 RSA2
     */
    private String signType = "RSA2";

    private int qrCodeWidth = 300;

    private int qrCodeHeight = 300;
}

/**
 * 微信支付属性
 *
 * @author zak
 * @since 1.0.0
 */
@Data
class WxPayProperties {

    /**
     * 是否使用沙箱环境
     */
    private boolean useSandbox = false;

    /**
     * appid
     */
    private String appid;

    /**
     * 商户id
     */
    private String mchId;

    /**
     * 微信支付key
     */
    private String key;

    /**
     * 证书路径
     * 支持file路径，如 file:D:\\test.cert,
     * 也可不加file:前缀，默认就是file协议
     * 支持classpath路径， 如 classpath:test.cert
     * 支持http链接， 如 http://www.baidu.com/test.cert
     */
    private String certLocation;

    private int qrCodeWidth = 300;

    private int qrCodeHeight = 300;

}

/**
 * 端点配置属性
 * @author zak
 * @since 1.0.0
 */
@Data
class EndpointProperties {

    private boolean enabled = true;

}