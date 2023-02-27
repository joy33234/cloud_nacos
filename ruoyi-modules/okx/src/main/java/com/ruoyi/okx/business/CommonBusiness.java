package com.ruoyi.okx.business;

import com.ruoyi.okx.enums.OrderStatusEnum;
import org.springframework.stereotype.Component;

@Component
public class CommonBusiness {
    public static Integer getOrderStatus(String status) {
        switch (status) {
            case "canceled":
                return OrderStatusEnum.FAIL.getStatus();
            case "live":
                return OrderStatusEnum.PENDING.getStatus();
            case "partially_filled":
                return OrderStatusEnum.PARTIALLYFILLED.getStatus();
            case "filled":
                return OrderStatusEnum.SUCCESS.getStatus();
        }
        return null;
    }
}
