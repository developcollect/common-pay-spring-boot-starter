package com.developcollect.commonpay.autoconfig;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.config.*;
import com.developcollect.commonpay.notice.IPayBroadcaster;
import com.developcollect.commonpay.notice.IRefundBroadcaster;
import com.developcollect.commonpay.pay.IOrder;
import com.developcollect.commonpay.pay.IRefund;
import com.developcollect.commonpay.utils.LambdaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 支付自动配置
 *
 * @author zak
 * @since 1.0.0
 */
@Slf4j
@ComponentScan(basePackages = "com.developcollect.commonpay.autoconfig.controller")
@EnableConfigurationProperties(CommonPayProperties.class)
@Import({GlobalConfig.class, CommonPayWebMvcConfig.class, CommonPaySpringUtil.class})
@Configuration
@RequiredArgsConstructor
public class CommonPayAutoConfig {

    @Autowired
    public void globalConfig(
            GlobalConfig globalConfig,
            CommonPayConfigurer commonPayConfigurer
    ) {
        // 进行配置
        commonPayConfigurer.config(globalConfig);

        // 配置完成  调用初始化方法
        ReflectUtil.invoke(globalConfig, "init");
    }


    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @ConditionalOnMissingBean(CommonPayConfigurer.class)
    @Configuration
    @RequiredArgsConstructor
    static class DefaultCommonPayConfigurer implements CommonPayConfigurer {

        private final CommonPayProperties commonPayProperties;

        private Supplier<AliPayConfig> aliPayConfigSupplier;

        private Supplier<WxPayConfig> wxPayConfigSupplier;

        private IPayFactory payFactory;

        private IPayBroadcaster payBroadcaster;

        private IRefundBroadcaster refundBroadcaster;

        @Autowired(required = false)
        @Qualifier("aliPayConfigSupplier")
        public void setAliPayConfigSupplier(Supplier<AliPayConfig> aliPayConfigSupplier) {
            this.aliPayConfigSupplier = aliPayConfigSupplier;
        }

        @Autowired(required = false)
        @Qualifier("wxPayConfigSupplier")
        public void setWxPayConfigSupplier(Supplier<WxPayConfig> wxPayConfigSupplier) {
            this.wxPayConfigSupplier = wxPayConfigSupplier;
        }

        @Autowired(required = false)
        public void setPayFactory(IPayFactory payFactory) {
            this.payFactory = payFactory;
        }

        @Autowired
        public void setPayBroadcaster(IPayBroadcaster payBroadcaster) {
            this.payBroadcaster = payBroadcaster;
        }

        @Autowired
        public void setRefundBroadcaster(IRefundBroadcaster refundBroadcaster) {
            this.refundBroadcaster = refundBroadcaster;
        }

        @Override
        public void config(GlobalConfig globalConfig) {
            checkContextPath(commonPayProperties.getContextPath());
            checkDomain(commonPayProperties.getDomain());

            Map<Integer, Supplier<? extends AbstractPayConfig>> payConfigSupplierMap = globalConfig.getPayConfigSupplierMap();

            globalConfig.setQueryNoticeDelay(commonPayProperties.getQueryNoticeDelay());

            if (aliPayConfigSupplier != null) {
                payConfigSupplierMap.put(PayPlatform.ALI_PAY, aliPayConfigSupplier);
                log.info("支付宝支付通知地址：{}/cPay/alipay", commonPayProperties.getUrlPrefix());
            } else {
                log.info("未找到支付宝支付配置");
            }
            if (wxPayConfigSupplier != null) {
                payConfigSupplierMap.put(PayPlatform.WX_PAY, wxPayConfigSupplier);
                log.info("微信支付通知地址：{}/cPay/wxpay", commonPayProperties.getUrlPrefix());
            } else {
                log.info("未找到微信支付配置");
            }

            globalConfig.setPayBroadcaster(payBroadcaster);
            globalConfig.setRefundBroadcaster(refundBroadcaster);

            if (payFactory != null) {
                globalConfig.setPayFactory(payFactory);
            }

        }

        // region 支付宝支付配置

        @ConditionalOnProperty(prefix = "develop-collect.pay", name = "alipay.appid")
        @ConditionalOnMissingBean(name = "aliPayConfigSupplier")
        @Bean
        Supplier<AliPayConfig> aliPayConfigSupplier(
                @Nullable @Qualifier("aliPayPcReturnUrlGenerator") Function<IOrder, String> aliPayPcReturnUrlGenerator,
                @Nullable @Qualifier("aliPayWapReturnUrlGenerator") Function<IOrder, String> aliPayWapReturnUrlGenerator,
                @Qualifier("aliPayPayNotifyUrlGenerator") Function<IOrder, String> aliPayPayNotifyUrlGenerator,
                @Qualifier("aliPayPayQrCodeAccessUrlGenerator") BiFunction<IOrder, String, String> aliPayPayQrCodeAccessUrlGenerator,
                @Qualifier("aliPayPcPayFormHtmlAccessUrlGenerator") BiFunction<IOrder, String, String> aliPayPcPayFormHtmlAccessUrlGenerator,
                @Qualifier("aliPayWapPayFormHtmlAccessUrlGenerator") BiFunction<IOrder, String, String> aliPayWapPayFormHtmlAccessUrlGenerator
        ) {
            AliPayProperties aliPayProperties = commonPayProperties.getAlipay();
            if (StrUtil.isBlank(aliPayProperties.getAppid())) {
                throw new IllegalArgumentException("alipay appid can not be blank");
            }
            if (StrUtil.isBlank(aliPayProperties.getPrivateKey())) {
                throw new IllegalArgumentException("alipay private key can not be blank");
            }
            if (StrUtil.isBlank(aliPayProperties.getPublicKey())) {
                throw new IllegalArgumentException("alipay public key can not be blank");
            }

            return new Supplier<AliPayConfig>() {

                private AliPayConfig aliPayConfig;

                {
                    aliPayConfig = new AliPayConfig();
                    aliPayConfig
                            .setAppId(aliPayProperties.getAppid())
                            .setPrivateKey(aliPayProperties.getPrivateKey())
                            .setPublicKey(aliPayProperties.getPublicKey())
                            .setCharset(aliPayProperties.getCharset())
                            .setSignType(aliPayProperties.getSignType())
                            .setDebug(aliPayProperties.isUseSandbox())
                            .setQrCodeWidth(aliPayProperties.getQrCodeWidth())
                            .setQrCodeHeight(aliPayProperties.getQrCodeHeight())
                            .setPcReturnUrlGenerator(aliPayPcReturnUrlGenerator)
                            .setWapReturnUrlGenerator(aliPayWapReturnUrlGenerator)
                            .setPayNotifyUrlGenerator(aliPayPayNotifyUrlGenerator)
                            .setPayQrCodeAccessUrlGenerator(aliPayPayQrCodeAccessUrlGenerator)
                            .setPcPayFormHtmlAccessUrlGenerator(aliPayPcPayFormHtmlAccessUrlGenerator)
                            .setWapPayFormHtmlAccessUrlGenerator(aliPayWapPayFormHtmlAccessUrlGenerator);
                }

                @Override
                public AliPayConfig get() {
                    return aliPayConfig;
                }
            };
        }

        @ConditionalOnMissingBean(name = "aliPayPayNotifyUrlGenerator")
        @Bean
        Function<IOrder, String> aliPayPayNotifyUrlGenerator() {
            return o -> String.format("%s/cPay/alipay", commonPayProperties.getUrlPrefix());
        }

        @ConditionalOnMissingBean(name = "aliPayPayQrCodeAccessUrlGenerator")
        @Bean
        BiFunction<IOrder, String, String> aliPayPayQrCodeAccessUrlGenerator() {
            return (order, content) -> payQrCodeAccessUrl(PayPlatform.ALI_PAY, order, content);
        }

        @ConditionalOnMissingBean(name = "aliPayPcPayFormHtmlAccessUrlGenerator")
        @Bean
        BiFunction<IOrder, String, String> aliPayPcPayFormHtmlAccessUrlGenerator() {
            return (order, content) -> pcPayFormHtmlAccessUrl(PayPlatform.ALI_PAY, order, content);
        }

        @ConditionalOnMissingBean(name = "aliPayWapPayFormHtmlAccessUrlGenerator")
        @Bean
        BiFunction<IOrder, String, String> aliPayWapPayFormHtmlAccessUrlGenerator() {
            return (order, content) -> wapPayFormHtmlAccessUrl(PayPlatform.ALI_PAY, order, content);
        }

        // endregion


        // region 微信支付配置

        @ConditionalOnProperty(prefix = "develop-collect.pay", name = "wxpay.appid")
        @ConditionalOnMissingBean(name = "wxPayConfigSupplier")
        @Bean
        Supplier<WxPayConfig> wxPayConfigSupplier(
                @Qualifier("wxPayPayNotifyUrlGenerator") Function<IOrder, String> wxPayPayNotifyUrlGenerator,
                @Qualifier("wxPayPayQrCodeAccessUrlGenerator") BiFunction<IOrder, String, String> wxPayPayQrCodeAccessUrlGenerator,
                @Qualifier("wxPayRefundNotifyUrlGenerator") BiFunction<IOrder, IRefund, String> wxPayRefundNotifyUrlGenerator,
                @Nullable @Qualifier("wxPayCertInputStreamSupplier") Supplier<InputStream> wxPayCertInputStreamSupplier
        ) {
            WxPayProperties wxPayProperties = commonPayProperties.getWxpay();
            if (StrUtil.isBlank(wxPayProperties.getAppid())) {
                throw new IllegalArgumentException("wxpay appid can not be blank");
            }
            if (StrUtil.isBlank(wxPayProperties.getMchId())) {
                throw new IllegalArgumentException("wxpay mchid can not be blank");
            }
            if (StrUtil.isBlank(wxPayProperties.getKey())) {
                throw new IllegalArgumentException("wxpay key can not be blank");
            }

            return new Supplier<WxPayConfig>() {
                private WxPayConfig wxPayConfig;

                {
                    wxPayConfig = new WxPayConfig();
                    wxPayConfig
                            .setAppId(wxPayProperties.getAppid())
                            .setMchId(wxPayProperties.getMchId())
                            .setKey(wxPayProperties.getKey())
                            .setCertInputStreamSupplier(wxPayCertInputStreamSupplier)
                            .setDebug(wxPayProperties.isUseSandbox())
                            .setQrCodeWidth(wxPayProperties.getQrCodeWidth())
                            .setQrCodeHeight(wxPayProperties.getQrCodeHeight())
                            .setPayNotifyUrlGenerator(wxPayPayNotifyUrlGenerator)
                            .setRefundNotifyUrlGenerator(wxPayRefundNotifyUrlGenerator)
                            .setPayQrCodeAccessUrlGenerator(wxPayPayQrCodeAccessUrlGenerator);
                }

                @Override
                public WxPayConfig get() {
                    return wxPayConfig;
                }
            };
        }

        @ConditionalOnMissingBean(name = "wxPayPayNotifyUrlGenerator")
        @Bean
        Function<IOrder, String> wxPayPayNotifyUrlGenerator() {
            return o -> String.format("%s/cPay/wxpay", commonPayProperties.getUrlPrefix());
        }

        @ConditionalOnMissingBean(name = "wxPayPayQrCodeAccessUrlGenerator")
        @Bean
        BiFunction<IOrder, String, String> wxPayPayQrCodeAccessUrlGenerator() {
            return (order, content) -> payQrCodeAccessUrl(PayPlatform.WX_PAY, order, content);
        }

        @ConditionalOnMissingBean(name = "wxPayRefundNotifyUrlGenerator")
        @Bean
        BiFunction<IOrder, IRefund, String> wxPayRefundNotifyUrlGenerator() {
            return (order, refund) -> String.format("%s/cPay/wxpay/refund", commonPayProperties.getUrlPrefix());
        }

        @ConditionalOnProperty(prefix = "develop-collect.pay", name = "wxpay.cert-location")
        @ConditionalOnMissingBean(name = "wxPayCertInputStreamSupplier")
        @Bean
        Supplier<InputStream> wxPayCertInputStreamSupplier() {
            return () -> LambdaUtil.doThrow(() -> getResource(commonPayProperties.getWxpay().getCertLocation()).getInputStream());
        }

        // endregion


        @ConditionalOnMissingBean(value = IPayBroadcaster.class)
        @Bean
        IPayBroadcaster payBroadcaster() {
            return payResponse -> {
                CommonPaySpringUtil.publishEvent(new PayEvent(payResponse));
                return true;
            };
        }

        @ConditionalOnMissingBean(value = IRefundBroadcaster.class)
        @Bean
        IRefundBroadcaster refundBroadcaster() {
            return refundResponse -> {
                CommonPaySpringUtil.publishEvent(new RefundEvent(refundResponse));
                return true;
            };
        }


        private String pcPayFormHtmlAccessUrl(int payPlatform, IOrder order, String html) {
            String payPlatformName = payPlatformName(payPlatform);
            File payHtmlFile = new File(String.format("%s/cPay/%s/%s.html", CommonPaySpringUtil.appHome(), payPlatformName, order.getOutTradeNo()));
            FileUtil.writeString(html, payHtmlFile, StandardCharsets.UTF_8);
            return String.format("%s/cPay/r/%s/%s.html", commonPayProperties.getUrlPrefix(), payPlatformName, order.getOutTradeNo());
        }

        private String wapPayFormHtmlAccessUrl(int payPlatform, IOrder order, String html) {
            String payPlatformName = payPlatformName(payPlatform);
            File payHtmlFile = new File(String.format("%s/cPay/%s/wap_%s.html", CommonPaySpringUtil.appHome(), payPlatformName, order.getOutTradeNo()));
            FileUtil.writeString(html, payHtmlFile, StandardCharsets.UTF_8);
            return String.format("%s/cPay/r/%s/wap_%s.html", commonPayProperties.getUrlPrefix(), payPlatformName, order.getOutTradeNo());
        }

        private String payQrCodeAccessUrl(int payPlatform, IOrder order, String content) {
            String payPlatformName = payPlatformName(payPlatform);
            File qrCodeFile = FileUtil.touch(String.format("%s/cPay/%s/%s.png", CommonPaySpringUtil.appHome(), payPlatformName, order.getOutTradeNo()));
            AbstractPayConfig payConfig = GlobalConfig.getPayConfig(payPlatform);
            int qrCodeWidth = payConfig.getQrCodeWidth();
            int qrCodeHeight = payConfig.getQrCodeHeight();
            QrCodeUtil.generate(content, qrCodeWidth, qrCodeHeight, qrCodeFile);
            return String.format("%s/cPay/r/%s/%s.png", commonPayProperties.getUrlPrefix(), payPlatformName, order.getOutTradeNo());
        }

        private String payPlatformName(int payPlatform) {
            switch (payPlatform) {
                case PayPlatform.ALI_PAY:
                    return "alipay";
                case PayPlatform.WX_PAY:
                    return "wxpay";
                default:
                    throw new IllegalArgumentException("不支持的支付平台");
            }
        }

        private void checkContextPath(String contextPath) {
            Assert.notNull(contextPath, "ContextPath must not be null");
            if (!contextPath.isEmpty()) {
                if ("/".equals(contextPath)) {
                    throw new IllegalArgumentException("Root ContextPath must be specified using an empty string");
                }

                if (!contextPath.startsWith("/") || contextPath.endsWith("/")) {
                    throw new IllegalArgumentException("ContextPath must start with '/' and not end with '/'");
                }
            }
        }

        private void checkDomain(String domain) {
            if (StrUtil.isBlank(domain)) {
                throw new IllegalArgumentException("domain can not be blank");
            }
        }

        private Resource getResource(String resourceLocation) {
            if (ResourceUtils.isUrl(resourceLocation)) {
                if (resourceLocation.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
                    return new ClassPathResource(resourceLocation.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()));
                } else {
                    try {
                        return new UrlResource(resourceLocation);
                    } catch (MalformedURLException e) {
                        // 因为前面有个isUrl判断, 所以这里不可能抛异常
                        throw new RuntimeException();
                    }
                }
            } else {
                return new FileSystemResource(resourceLocation);
            }
        }
    }

}
