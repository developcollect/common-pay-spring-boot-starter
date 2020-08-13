package com.developcollect.commonpay.autoconfig;

import com.developcollect.commonpay.pay.PayResponse;
import org.springframework.context.ApplicationEvent;

/**
 * 支付事件
 *
 * @author zak
 * @since 1.0.0
 */
public class PayEvent extends ApplicationEvent {
    public PayEvent(PayResponse source) {
        super(source);
    }

    public PayResponse getPayResponse() {
        return (PayResponse) super.getSource();
    }
}
