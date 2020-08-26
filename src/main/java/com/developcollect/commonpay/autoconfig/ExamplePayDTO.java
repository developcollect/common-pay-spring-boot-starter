package com.developcollect.commonpay.autoconfig;

import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.pay.IPayDTO;
import lombok.AllArgsConstructor;

/**
 * @author zak
 * @since 1.0.0
 */
@AllArgsConstructor
class ExamplePayDTO implements IPayDTO {

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

    public static ExamplePayDTO getAliPayExamplePayDTO() {
        return new ExamplePayDTO("{outTradeNo}", "{tradeNo}", 100L, PayPlatform.ALI_PAY);
    }

    public static ExamplePayDTO getWxPayExamplePayDTO() {
        return new ExamplePayDTO("{outTradeNo}", "{tradeNo}", 100L, PayPlatform.WX_PAY);
    }
}
