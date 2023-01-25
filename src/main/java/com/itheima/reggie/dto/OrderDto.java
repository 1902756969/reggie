package com.itheima.reggie.dto;


import com.itheima.reggie.entity.OrderDetail;
import com.itheima.reggie.entity.Orders;
import lombok.Data;

import java.util.List;

@Data
public class OrderDto extends Orders {

    //记录订单的数量
    private Integer sumNum;

    //记录下单用户的名字
    private String consignee;

    //记录订单中具体的菜品
    private List<OrderDetail> orderDetails;
}
