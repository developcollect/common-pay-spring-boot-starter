package com.developcollect.commonpay.autoconfig;

import com.developcollect.commonpay.pay.RefundResponse;
import org.springframework.context.ApplicationEvent;

/**
 * 退款结果事件
 *
 * @author zak
 * @since 1.0.0
 */
public class RefundEvent extends ApplicationEvent {

    public RefundEvent(RefundResponse source) {
        super(source);
    }

    public RefundResponse getRefundResponse() {
        return (RefundResponse) super.getSource();
    }
}
