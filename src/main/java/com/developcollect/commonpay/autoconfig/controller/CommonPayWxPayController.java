package com.developcollect.commonpay.autoconfig.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.SecureUtil;
import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.config.GlobalConfig;
import com.developcollect.commonpay.config.WxPayConfig;
import com.developcollect.commonpay.pay.PayResponse;
import com.developcollect.commonpay.pay.RefundResponse;
import com.developcollect.commonpay.pay.wxpay.sdk.WXPayConstants;
import com.developcollect.commonpay.pay.wxpay.sdk.WXPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 微信支付通知接收控制器
 * @author zak
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnExpression("${develop-collect.pay.notify-endpoint.enabled:true}")
@RestController
@RequestMapping("/cPay")
public class CommonPayWxPayController extends BaseController {

    private static final String SUCCESS_RET = "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
    private static final String FAILURE_RET = "";

    /**
     * 微信支付结果通知
     *
     * @author zak
     * @since 1.0.0
     */
    @PostMapping("/wxpay")
    public String payNotify(HttpServletRequest request) {
        try {
            Map<String, String> params = getParamsFromXmlBody(request, "xml");

            // 签名验证
            if (!signVerify(params)) {
                return FAILURE_RET;
            }

            // 转换为支付结果对象
            PayResponse payResponse = toPayResponse(params);

            // 发送广播
            if (GlobalConfig.payBroadcaster().broadcast(payResponse)) {
                try {
                    Consumer<PayResponse> wxPayTempFileClear = GlobalConfig
                            .getPayConfig(PayPlatform.ALI_PAY)
                            .getExtend("wxPayTempFileClear");
                    if (wxPayTempFileClear != null) {
                        wxPayTempFileClear.accept(payResponse);
                    }
                } catch (Exception e) {
                    log.debug("清除临时文件失败", e);
                }
                return SUCCESS_RET;
            }
        } catch (Exception e) {
            log.error("微信支付结果异步通知处理失败", e);
        }

        return FAILURE_RET;
    }


    private PayResponse toPayResponse(Map<String, String> params) {
        PayResponse payResponse = new PayResponse();
        payResponse
                .setSuccess(true)
                .setRawObj((Serializable) params)
                .setPayPlatform(PayPlatform.WX_PAY)
                .setPayTime(DateUtil.parseLocalDateTime(params.get("time_end"), "yyyyMMddHHmmss"))
                .setTradeNo(params.get("transaction_id"))
                .setOutTradeNo(params.get("out_trade_no"));
        return payResponse;
    }


    /**
     * 微信退款结果通知
     * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=9_16&index=11
     * 解密步骤如下：
     * （1）对加密串A做base64解码，得到加密串B
     * （2）对商户key做md5，得到32位小写key* ( key设置路径：微信商户平台(pay.weixin.qq.com)-->账户设置-->API安全-->密钥设置 )
     * （3）用key*对加密串B做AES-256-ECB解密（PKCS7Padding）
     *
     * @author zak
     * @since 1.0.0
     */
    @PostMapping("/wxpay/refund")
    public String refundNotify(HttpServletRequest request) throws IOException {
        try {
            Map<String, String> params = getParamsFromXmlBody(request, "xml");
            WxPayConfig payConfig = GlobalConfig.getPayConfig(PayPlatform.WX_PAY);
            String reqInfo = params.get("req_info");

            byte[] reqInfoDecode = Base64.decode(reqInfo);
            String md5Key = SecureUtil.md5(payConfig.getKey()).toLowerCase();
            String reqInfoDecryptStr = SecureUtil.aes(md5Key.getBytes()).decryptStr(reqInfoDecode);
            Map<String, String> reqInfoMap = getParamsFromXmlStr(reqInfoDecryptStr, "root");

            params.putAll(reqInfoMap);

            // 转换为退款结果对象
            RefundResponse refundResponse = toRefundResponse(params);

            // 发送广播
            if (GlobalConfig.refundBroadcaster().broadcast(refundResponse)) {
                return SUCCESS_RET;
            }
        } catch (Exception e) {
            log.error("微信退款结果异步通知处理失败", e);
        }

        return FAILURE_RET;
    }


    private RefundResponse toRefundResponse(Map<String, String> params) {
        RefundResponse refundResponse = new RefundResponse();
        refundResponse
                .setSuccess("SUCCESS".equals(params.get("refund_status")))
                .setRawObj((Serializable) params)
                .setPayPlatform(PayPlatform.WX_PAY)
                .setRefundNo(params.get("refund_id"))
                .setOutRefundNo(params.get("out_refund_no"))
                .setRefundTime(DateUtil.parseLocalDateTime(params.get("success_time"), "yyyy-MM-dd HH:mm:ss"));
        return refundResponse;
    }


    /**
     * 签名校验
     * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=4_3
     *
     * @param params 支付通知参数
     * @return 校验是否通过
     * @author zak
     * @since 1.0.0
     */
    private boolean signVerify(Map<String, String> params) {
        WxPayConfig payConfig = GlobalConfig.getPayConfig(PayPlatform.WX_PAY);
        boolean verify;

        try {
            // 在微信的支付SDK中沙箱环境是用的md5签名
            verify = WXPayUtil.isSignatureValid(
                    params,
                    payConfig.getKey(),
                    payConfig.isDebug() ? WXPayConstants.SignType.MD5 : WXPayConstants.SignType.HMACSHA256
            );
        } catch (Exception e) {
            verify = false;
        }

        if (!verify) {
            log.warn("微信支付结果异步通知验签不通过: [{}]", params);
        }

        return verify;
    }
}
