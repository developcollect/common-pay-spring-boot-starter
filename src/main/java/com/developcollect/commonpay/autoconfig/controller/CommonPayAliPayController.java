package com.developcollect.commonpay.autoconfig.controller;

import cn.hutool.core.date.DateUtil;
import com.alipay.api.AlipayApiException;
import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.config.AliPayConfig;
import com.developcollect.commonpay.config.GlobalConfig;
import com.developcollect.commonpay.pay.PayResponse;
import com.developcollect.commonpay.pay.alipay.utils.AlipaySignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 支付宝通知接收控制器
 *
 * @author zak
 * @since 1.0.0
 */
@Slf4j
@RestController
@ConditionalOnExpression("${develop-collect.pay.notify-endpoint.enabled:true}")
@RequestMapping("/cPay")
public class CommonPayAliPayController extends BaseController {

    private static final String SUCCESS_RET = "success";
    private static final String FAILURE_RET = "failure";


    /**
     * 支付宝支付通知
     *
     * @param request request
     * @author zak
     * @since 1.0.0
     */
    @PostMapping("/alipay")
    public String alipayNotify(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);

            // 签名验证
            if (!signVerify(params)) {
                return FAILURE_RET;
            }

            // 转换成支付结果对象
            PayResponse payResponse = toPayResponse(params);

            // 发送广播
            if (GlobalConfig.payBroadcaster().broadcast(payResponse)) {
                try {
                    Consumer<PayResponse> aliPayTempFileClear = GlobalConfig
                            .getPayConfig(PayPlatform.ALI_PAY)
                            .getExt("aliPayTempFileClear");
                    if (aliPayTempFileClear != null) {
                        aliPayTempFileClear.accept(payResponse);
                    }
                } catch (Exception e) {
                    log.debug("清除临时文件失败", e);
                }

                return SUCCESS_RET;
            }


        } catch (Exception e) {
            log.error("支付宝支付结果异步通知处理失败", e);
        }
        return FAILURE_RET;
    }


    private PayResponse toPayResponse(Map<String, String> params) {
        PayResponse payResponse = new PayResponse();
        payResponse
                .setSuccess(true)
                .setRawObj((Serializable) params)
                .setPayPlatform(PayPlatform.ALI_PAY)
                .setTradeNo(params.get("trade_no"))
                .setPayTime(DateUtil.parseLocalDateTime(params.get("notify_time")))
                .setOutTradeNo(params.get("out_trade_no"));
        return payResponse;
    }


    // 支付宝退款通知

    // 支付宝转账通知


    /**
     * 签名验证
     *
     * @param params 支付通知参数
     * @return boolean 校验是否通过
     * @author zak
     * @since 1.0.0
     */
    private boolean signVerify(Map<String, String> params) {
        AliPayConfig payConfig = GlobalConfig.getPayConfig(PayPlatform.ALI_PAY);
        boolean verify;
        try {
            if (payConfig.hasCert()) {
                verify = AlipaySignature.rsaCertContentCheckV1(params, payConfig.getAlipayCertContentSupplier().get(), payConfig.getCharset(), payConfig.getSignType());
            } else {
                verify = AlipaySignature.rsaCheckV1(params, payConfig.getPublicKey(), payConfig.getCharset(), payConfig.getSignType());
            }
        } catch (AlipayApiException e) {
            verify = false;
        }

        if (!verify) {
            log.warn("支付宝支付结果异步通知验签不通过: [{}]", params);
        }

        return verify;
    }
}
