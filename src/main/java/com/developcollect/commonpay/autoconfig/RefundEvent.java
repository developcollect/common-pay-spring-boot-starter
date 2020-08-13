package com.developcollect.commonpay.autoconfig;

import com.developcollect.commonpay.pay.RefundResponse;
import org.springframework.context.ApplicationEvent;

/**
 * 退款结果事件
 *
 * @author Zhu Kaixiao
 * @version 1.0
 * @date 2020/8/10 17:17
 * @copyright 江西金磊科技发展有限公司 All rights reserved. Notice
 * 仅限于授权后使用，禁止非授权传阅以及私自用于商业目的。
 */
public class RefundEvent extends ApplicationEvent {

    public RefundEvent(RefundResponse source) {
        super(source);
    }

    public RefundResponse getRefundResponse() {
        return (RefundResponse) super.getSource();
    }
}
