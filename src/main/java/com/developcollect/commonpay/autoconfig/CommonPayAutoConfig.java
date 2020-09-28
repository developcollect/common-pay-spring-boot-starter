package com.developcollect.commonpay.autoconfig;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.config.*;
import com.developcollect.commonpay.notice.*;
import com.developcollect.commonpay.pay.IPayDTO;
import com.developcollect.commonpay.pay.IRefundDTO;
import com.developcollect.commonpay.pay.PayResponse;
import com.developcollect.dcinfra.utils.LambdaUtil;
import com.developcollect.dcinfra.utils.spring.SpringUtil;
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
import java.util.function.Consumer;
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
@Import({GlobalConfig.class, CommonPayWebMvcConfig.class, SpringUtil.class})
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


    /**
     * 默认配置器
     */
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @ConditionalOnMissingBean(CommonPayConfigurer.class)
    @Configuration
    @RequiredArgsConstructor
    static class DefaultCommonPayConfigurer implements CommonPayConfigurer {

        /**
         * 支付配置属性
         */
        private final CommonPayProperties commonPayProperties;

        /**
         * 支付宝配置提供器
         */
        private Supplier<AliPayConfig> aliPayConfigSupplier;

        /**
         * 微信配置提供器
         */
        private Supplier<WxPayConfig> wxPayConfigSupplier;

        /**
         * Pay工厂
         */
        private IPayFactory payFactory;

        /**
         * 支付结果广播器
         */
        private IPayBroadcaster payBroadcaster;

        /**
         * 退款结果广播器
         */
        private IRefundBroadcaster refundBroadcaster;

        /**
         * 未确认订单提取器
         */
        private IUnconfirmedOrderFetcher unconfirmedOrderFetcher;

        /**
         * 未确认提现单提取器
         */
        private IUnconfirmedTransferFetcher unconfirmedTransferFetcher;

        /**
         * 未确认退款单提取器
         */
        private IUnconfirmedRefundFetcher unconfirmedRefundFetcher;


        // 注意自动注入是指定了bean的名称的

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

        @Autowired(required = false)
        public void setUnconfirmedOrderFetcher(IUnconfirmedOrderFetcher unconfirmedOrderFetcher) {
            this.unconfirmedOrderFetcher = unconfirmedOrderFetcher;
        }

        @Autowired(required = false)
        public void setUnconfirmedRefundFetcher(IUnconfirmedRefundFetcher unconfirmedRefundFetcher) {
            this.unconfirmedRefundFetcher = unconfirmedRefundFetcher;
        }

        @Autowired(required = false)
        public void setUnconfirmedTransferFetcher(IUnconfirmedTransferFetcher unconfirmedTransferFetcher) {
            this.unconfirmedTransferFetcher = unconfirmedTransferFetcher;
        }

        @Override
        public void config(GlobalConfig globalConfig) {
            // 校验配置的值
            checkContextPath(commonPayProperties.getContextPath());
            checkDomain(commonPayProperties.getDomain());

            // 支付平台 与 支付配置提供器 map
            // 后续的支付过程会根据支付平台到这个map中取相应的支付配置提供器，从而拿到相应的支付配置
            Map<Integer, Supplier<? extends AbstractPayConfig>> payConfigSupplierMap = globalConfig.getPayConfigSupplierMap();

            // 设置主动查询通知间隔时间， 用于控制主动查询支付状态的频率
            globalConfig.setQueryNoticeDelay(commonPayProperties.getQueryNoticeDelay());

            // 设置支付宝配置提供器
            if (aliPayConfigSupplier != null) {
                payConfigSupplierMap.put(PayPlatform.ALI_PAY, aliPayConfigSupplier);
                try {
                    String aliPayNotifyUrlExample = aliPayConfigSupplier.get().getPayNotifyUrlGenerator().apply(ExamplePayDTO.getAliPayExamplePayDTO());
                    log.info("支付宝支付通知地址：{}", aliPayNotifyUrlExample);
                } catch (Exception e) {
                    log.warn("支付宝支付通知示例地址生成失败", e);
                }
            } else {
                log.info("未找到支付宝支付配置");
            }

            // 设置微信配置提供器
            if (wxPayConfigSupplier != null) {
                payConfigSupplierMap.put(PayPlatform.WX_PAY, wxPayConfigSupplier);
                try {
                    String wxPayNotifyUrlExample = wxPayConfigSupplier.get().getPayNotifyUrlGenerator().apply(ExamplePayDTO.getWxPayExamplePayDTO());
                    log.info("微信支付通知地址：{}", wxPayNotifyUrlExample);
                } catch (Exception e) {
                    log.warn("微信支付通知示例地址生成失败", e);
                }
            } else {
                log.info("未找到微信支付配置");
            }

            // 设置支付结果广播器（必须）
            globalConfig.setPayBroadcaster(payBroadcaster);
            // 设置退款结果广播器
            globalConfig.setRefundBroadcaster(refundBroadcaster);

            // 设置Pay工厂， 因为common-pay中有默认的工厂， 所以只有在payFactory不为null的时候才覆盖原有的工厂
            if (payFactory != null) {
                globalConfig.setPayFactory(payFactory);
            }

            // 设置未确认支付订单提取器
            globalConfig.setUnconfirmedOrderFetcher(unconfirmedOrderFetcher);
            globalConfig.setUnconfirmedRefundFetcher(unconfirmedRefundFetcher);
            globalConfig.setUnconfirmedTransferFetcher(unconfirmedTransferFetcher);
        }

        // region 支付宝支付配置

        /**
         * 支付宝配置提供器
         * 注意： 注入的各参数都有指定Bean名称
         * @param aliPayPcReturnUrlGenerator 支付宝PC页面支付完成跳转地址生成器
         * @param aliPayWapReturnUrlGenerator 支付宝WAP页面支付完成跳转地址生成器
         * @param aliPayPayNotifyUrlGenerator 支付宝支付结果异步通知地址生成器
         * @param aliPayPayQrCodeAccessUrlGenerator 支付宝支付二维码访问地址生成器
         * @param aliPayPcPayFormHtmlAccessUrlGenerator 支付宝PC页面支付页面访问地址生成器
         * @param aliPayWapPayFormHtmlAccessUrlGenerator 支付宝WAP页面支付页面访问地址生成器
         */
        @ConditionalOnProperty(prefix = "develop-collect.pay", name = "alipay.appid")
        @ConditionalOnMissingBean(name = "aliPayConfigSupplier")
        @Bean
        Supplier<AliPayConfig> aliPayConfigSupplier(
                @Nullable @Qualifier("aliPayPcReturnUrlGenerator") Function<IPayDTO, String> aliPayPcReturnUrlGenerator,
                @Nullable @Qualifier("aliPayWapReturnUrlGenerator") Function<IPayDTO, String> aliPayWapReturnUrlGenerator,
                @Qualifier("aliPayPayNotifyUrlGenerator") Function<IPayDTO, String> aliPayPayNotifyUrlGenerator,
                @Qualifier("aliPayPayQrCodeAccessUrlGenerator") BiFunction<IPayDTO, String, String> aliPayPayQrCodeAccessUrlGenerator,
                @Qualifier("aliPayPcPayFormHtmlAccessUrlGenerator") BiFunction<IPayDTO, String, String> aliPayPcPayFormHtmlAccessUrlGenerator,
                @Qualifier("aliPayWapPayFormHtmlAccessUrlGenerator") BiFunction<IPayDTO, String, String> aliPayWapPayFormHtmlAccessUrlGenerator,
                @Qualifier("aliPayTempFileClear") Consumer<PayResponse> aliPayTempFileClear
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
                            .setWapPayFormHtmlAccessUrlGenerator(aliPayWapPayFormHtmlAccessUrlGenerator)
                            // 扩展配置
                            .putExt("aliPayTempFileClear", aliPayTempFileClear);
                }

                @Override
                public AliPayConfig get() {
                    return aliPayConfig;
                }
            };
        }

        /**
         * 支付宝支付结果异步通知地址生成器
         */
        @ConditionalOnMissingBean(name = "aliPayPayNotifyUrlGenerator")
        @Bean
        Function<IPayDTO, String> aliPayPayNotifyUrlGenerator() {
            return o -> String.format("%s/cPay/alipay", commonPayProperties.getUrlPrefix());
        }

        /**
         * 支付宝支付二维码访问地址生成器
         */
        @ConditionalOnMissingBean(name = "aliPayPayQrCodeAccessUrlGenerator")
        @Bean
        BiFunction<IPayDTO, String, String> aliPayPayQrCodeAccessUrlGenerator() {
            return (payDTO, content) -> payQrCodeAccessUrl(PayPlatform.ALI_PAY, payDTO, content);
        }

        /**
         * 支付宝PC支付页面访问地址生成器
         */
        @ConditionalOnMissingBean(name = "aliPayPcPayFormHtmlAccessUrlGenerator")
        @Bean
        BiFunction<IPayDTO, String, String> aliPayPcPayFormHtmlAccessUrlGenerator() {
            return (payDTO, content) -> pcPayFormHtmlAccessUrl(PayPlatform.ALI_PAY, payDTO, content);
        }

        /**
         * 支付宝WAP支付页面访问地址生成器
         */
        @ConditionalOnMissingBean(name = "aliPayWapPayFormHtmlAccessUrlGenerator")
        @Bean
        BiFunction<IPayDTO, String, String> aliPayWapPayFormHtmlAccessUrlGenerator() {
            return (payDTO, content) -> wapPayFormHtmlAccessUrl(PayPlatform.ALI_PAY, payDTO, content);
        }


        @ConditionalOnMissingBean(name = "aliPayTempFileClear")
        @Bean
        Consumer<PayResponse> aliPayTempFileClear() {
            return this::clearTempFile;
        }

        // endregion


        // region 微信支付配置

        /**
         * 微信配置提供其
         * @param wxPayPayNotifyUrlGenerator 微信支付结果异步通知地址生成器
         * @param wxPayPayQrCodeAccessUrlGenerator 微信支付二维码访问地址生成器
         * @param wxPayRefundNotifyUrlGenerator 微信退款结果异步通知地址生成器
         * @param wxPayCertInputStreamSupplier 微信接口调用证书提供器(有的功能需要证书)
         */
        @ConditionalOnProperty(prefix = "develop-collect.pay", name = "wxpay.appid")
        @ConditionalOnMissingBean(name = "wxPayConfigSupplier")
        @Bean
        Supplier<WxPayConfig> wxPayConfigSupplier(
                @Qualifier("wxPayPayNotifyUrlGenerator") Function<IPayDTO, String> wxPayPayNotifyUrlGenerator,
                @Qualifier("wxPayPayQrCodeAccessUrlGenerator") BiFunction<IPayDTO, String, String> wxPayPayQrCodeAccessUrlGenerator,
                @Qualifier("wxPayRefundNotifyUrlGenerator") BiFunction<IPayDTO, IRefundDTO, String> wxPayRefundNotifyUrlGenerator,
                @Nullable @Qualifier("wxPayCertInputStreamSupplier") Supplier<InputStream> wxPayCertInputStreamSupplier,
                @Qualifier("wxPayTempFileClear") Consumer<PayResponse> wxPayTempFileClear
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
                            .setPayQrCodeAccessUrlGenerator(wxPayPayQrCodeAccessUrlGenerator)
                            // 扩展配置
                            .putExt("wxPayTempFileClear", wxPayTempFileClear);
                }

                @Override
                public WxPayConfig get() {
                    return wxPayConfig;
                }
            };
        }

        /**
         * 微信支付结果异步通知地址生成器
         */
        @ConditionalOnMissingBean(name = "wxPayPayNotifyUrlGenerator")
        @Bean
        Function<IPayDTO, String> wxPayPayNotifyUrlGenerator() {
            return o -> String.format("%s/cPay/wxpay", commonPayProperties.getUrlPrefix());
        }

        /**
         * 微信支付二维码访问地址生成器
         */
        @ConditionalOnMissingBean(name = "wxPayPayQrCodeAccessUrlGenerator")
        @Bean
        BiFunction<IPayDTO, String, String> wxPayPayQrCodeAccessUrlGenerator() {
            return (payDTO, content) -> payQrCodeAccessUrl(PayPlatform.WX_PAY, payDTO, content);
        }

        /**
         * 微信退款结果异步通知地址生成器
         */
        @ConditionalOnMissingBean(name = "wxPayRefundNotifyUrlGenerator")
        @Bean
        BiFunction<IPayDTO, IRefundDTO, String> wxPayRefundNotifyUrlGenerator() {
            return (payDTO, refundDTO) -> String.format("%s/cPay/wxpay/refund", commonPayProperties.getUrlPrefix());
        }

        /**
         * 微信接口调用证书提供其
         * 微信退款时需要证书
         */
        @ConditionalOnProperty(prefix = "develop-collect.pay", name = "wxpay.cert-location")
        @ConditionalOnMissingBean(name = "wxPayCertInputStreamSupplier")
        @Bean
        Supplier<InputStream> wxPayCertInputStreamSupplier() {
            return () -> LambdaUtil.doThrow(() -> getResource(commonPayProperties.getWxpay().getCertLocation()).getInputStream());
        }

        @ConditionalOnMissingBean(name = "wxPayTempFileClear")
        @Bean
        Consumer<PayResponse> wxPayTempFileClear() {
            return this::clearTempFile;
        }
        // endregion


        /**
         * 支付结果广播器
         */
        @ConditionalOnMissingBean(value = IPayBroadcaster.class)
        @Bean
        IPayBroadcaster payBroadcaster() {
            return payResponse -> {
                SpringUtil.publishEvent(new PayEvent(payResponse));
                return true;
            };
        }

        /**
         * 退款结果广播器
         */
        @ConditionalOnMissingBean(value = IRefundBroadcaster.class)
        @Bean
        IRefundBroadcaster refundBroadcaster() {
            return refundResponse -> {
                SpringUtil.publishEvent(new RefundEvent(refundResponse));
                return true;
            };
        }


        private String pcPayFormHtmlAccessUrl(int payPlatform, IPayDTO payDTO, String html) {
            String payPlatformName = payPlatformName(payPlatform);
            File payHtmlFile = new File(String.format("%s/cPay/%s/%s.html", SpringUtil.appHome(), payPlatformName, payDTO.getOutTradeNo()));
            FileUtil.writeString(html, payHtmlFile, StandardCharsets.UTF_8);
            return String.format("%s/cPay/r/%s/%s.html", commonPayProperties.getUrlPrefix(), payPlatformName, payDTO.getOutTradeNo());
        }

        private String wapPayFormHtmlAccessUrl(int payPlatform, IPayDTO payDTO, String html) {
            String payPlatformName = payPlatformName(payPlatform);
            File payHtmlFile = new File(String.format("%s/cPay/%s/wap_%s.html", SpringUtil.appHome(), payPlatformName, payDTO.getOutTradeNo()));
            FileUtil.writeString(html, payHtmlFile, StandardCharsets.UTF_8);
            return String.format("%s/cPay/r/%s/wap_%s.html", commonPayProperties.getUrlPrefix(), payPlatformName, payDTO.getOutTradeNo());
        }

        private String payQrCodeAccessUrl(int payPlatform, IPayDTO payDTO, String content) {
            String payPlatformName = payPlatformName(payPlatform);
            File qrCodeFile = FileUtil.touch(String.format("%s/cPay/%s/%s.png", SpringUtil.appHome(), payPlatformName, payDTO.getOutTradeNo()));
            AbstractPayConfig payConfig = GlobalConfig.getPayConfig(payPlatform);
            int qrCodeWidth = payConfig.getQrCodeWidth();
            int qrCodeHeight = payConfig.getQrCodeHeight();
            QrCodeUtil.generate(content, qrCodeWidth, qrCodeHeight, qrCodeFile);
            return String.format("%s/cPay/r/%s/%s.png", commonPayProperties.getUrlPrefix(), payPlatformName, payDTO.getOutTradeNo());
        }

        private void clearTempFile(PayResponse payResponse) {
            String payPlatformName = payPlatformName(payResponse.getPayPlatform());
            String appHome = SpringUtil.appHome();
            String outTradeNo = payResponse.getOutTradeNo();
            FileUtil.del(String.format("%s/cPay/%s/%s.png", appHome, payPlatformName, outTradeNo));
            FileUtil.del(String.format("%s/cPay/%s/%s.html", appHome, payPlatformName, outTradeNo));
            FileUtil.del(String.format("%s/cPay/%s/wap_%s.html", appHome, payPlatformName, outTradeNo));
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
