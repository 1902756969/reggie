package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.OrderDto;
import com.itheima.reggie.entity.OrderDetail;
import com.itheima.reggie.entity.Orders;
import com.itheima.reggie.entity.ShoppingCart;
import com.itheima.reggie.service.OrderDetailService;
import com.itheima.reggie.service.OrderService;
import com.itheima.reggie.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 订单管理
 */
@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService ordersService;

    @Autowired
    private OrderDetailService orderDetailService;

    /**
     * 用户下单
     * @param orders
     * @return
     */
    @PostMapping("/submit")
    public R<String> submit(@RequestBody Orders orders){
        log.info("订单数据:{}",orders);
        ordersService.submit(orders);
        return R.success("下单成功");
    }

    /**
     * 订单信息分页查询
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/userPage")
    public R<Page> page(int page,int pageSize){
        log.info("page = {},pageSize = {}",page,pageSize);

        //获取用户id
        Long currentId = BaseContext.getCurrentId();

        //构造分页构造器
        Page<Orders> pageInfo = new Page(page,pageSize);
        Page<OrderDto> dtoPage = new Page<>();

        //构造条件构造器
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();

        //添加查询条件，根据用户id进行查询
        queryWrapper.eq(Orders::getUserId,currentId);

        //添加排序条件，根据更新时间进行排序
        queryWrapper.orderByDesc(Orders::getCheckoutTime);

        ordersService.page(pageInfo,queryWrapper);

        //对象拷贝
        BeanUtils.copyProperties(pageInfo,dtoPage,"records");
        List<Orders> orders = pageInfo.getRecords();

        List<OrderDto> list = orders.stream().map((item) -> {
            OrderDto ordersDto = new OrderDto();
            //对象拷贝
            BeanUtils.copyProperties(item,ordersDto);
            //构造条件构造器
            LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
            //订单id
            Long id = item.getId();
            wrapper.eq(OrderDetail::getOrderId,id);
            //根据id查询订单详细表中的数据数量
            List<OrderDetail> orderDetailList = orderDetailService.list(wrapper);
            ordersDto.setSumNum(orderDetailList.size());
            ordersDto.setOrderDetails(orderDetailList);
            return ordersDto;
        }).collect(Collectors.toList());

        dtoPage.setRecords(list);

        return R.success(dtoPage);
    }

    /**
     * 再来一单
     * @param order
     * @return
     */
    @PostMapping("/again")
    public R<String> again(@RequestBody Orders order){



        return R.success("下单成功");
    }

    /**
     * 订单明细分页查询
     * @param page
     * @param pageSize
     * @param number
     * @param beginTime
     * @param endTime
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page,int pageSize,Long number,String beginTime,String endTime){
        log.info("page = {},pageSize = {},number = {},beginTime = {},endTime = {}",page,pageSize,number,beginTime,endTime);

        //构造分页构造器
        Page<Orders> pageInfo = new Page(page,pageSize);

        //构造条件构造器
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();

        //添加过滤条件
        queryWrapper.like(number!=null,Orders::getNumber,number);
        queryWrapper.gt(!org.springframework.util.StringUtils.isEmpty(beginTime),Orders::getOrderTime,beginTime);
        queryWrapper.lt(!StringUtils.isEmpty(endTime),Orders::getOrderTime,endTime);

        //添加排序条件
        queryWrapper.orderByDesc(Orders::getOrderTime);

        //执行查询
        ordersService.page(pageInfo,queryWrapper);

        return R.success(pageInfo);
    }

    /**
     * 修改订单状态
     * @param orders
     * @return
     */
    @PutMapping
    public R<String> modifyStatus(@RequestBody Orders orders){
        log.info("orders:{}",orders);
        Orders order = ordersService.getById(orders.getId());
        order.setStatus(orders.getStatus());
        ordersService.updateById(order);
        return R.success("状态修改成功");
    }
}
