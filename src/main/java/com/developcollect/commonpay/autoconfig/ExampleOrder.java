package com.developcollect.commonpay.autoconfig;

import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.pay.IOrder;
import lombok.AllArgsConstructor;

/**
 * @author Zhu Kaixiao
 * @version 1.0
 * @date 2020/8/19 10:10
 * @copyright 江西金磊科技发展有限公司 All rights reserved. Notice
 * 仅限于授权后使用，禁止非授权传阅以及私自用于商业目的。
 */
@AllArgsConstructor
class ExampleOrder implements IOrder {

    private String outTradeNo;
    private String tradeNo;
    private Long totalFee;
    private int payPlatform;



    @Override
    public String getOutTradeNo() {
        return outTradeNo;
    }

    @Override
    public String getTradeNo() {
        return tradeNo;
    }

    @Override
    public Long getTotalFee() {
        return totalFee;
    }

    @Override
    public int getPayPlatform() {
        return payPlatform;
    }

    @Override
    public Object getSource() {
        return null;
    }

    public static ExampleOrder getAliPayExampleOrder() {
        return new ExampleOrder("{outTradeNo}", "{tradeNo}", 100L, PayPlatform.ALI_PAY);
    }

    public static ExampleOrder getWxPayExampleOrder() {
        return new ExampleOrder("{outTradeNo}", "{tradeNo}", 100L, PayPlatform.WX_PAY);
    }
}
