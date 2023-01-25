package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.dto.SetmealDto;
import com.itheima.reggie.entity.*;
import com.itheima.reggie.mapper.OrderMapper;
import com.itheima.reggie.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {


    @Resource
    private ShoppingCartService shoppingCartService;

    @Resource
    private UserService userService;

    @Resource
    private AddressBookService addressBookService;

    @Resource
    private OrderService orderService;
    @Resource
    private OrderDetailService orderDetailService;


    @Resource
    private DishService dishService;
    @Resource
    private SetmealService setmealService;
    /**

    /**
     * 用户下单
     * @param orders
     */
    @Transactional
    public void submit(Orders orders) {
        //获得当前用户id
        Long userId = BaseContext.getCurrentId();

        //查询当前用户的购物车数据
        LambdaQueryWrapper<ShoppingCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShoppingCart::getUserId,userId);
        List<ShoppingCart> shoppingCarts = shoppingCartService.list(wrapper);

        if(shoppingCarts == null || shoppingCarts.size() == 0){
            throw new CustomException("购物车为空，不能下单");
        }

        //查询用户数据
        User user = userService.getById(userId);

        //查询地址数据
        Long addressBookId = orders.getAddressBookId();
        AddressBook addressBook = addressBookService.getById(addressBookId);
        if(addressBook == null){
            throw new CustomException("用户地址信息有误，不能下单");
        }

        long orderId = IdWorker.getId();//订单号

        AtomicInteger amount = new AtomicInteger(0);

        List<OrderDetail> orderDetails = shoppingCarts.stream().map((item) -> {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrderId(orderId);
            orderDetail.setNumber(item.getNumber());
            orderDetail.setDishFlavor(item.getDishFlavor());
            orderDetail.setDishId(item.getDishId());
            orderDetail.setSetmealId(item.getSetmealId());
            orderDetail.setName(item.getName());
            orderDetail.setImage(item.getImage());
            orderDetail.setAmount(item.getAmount());
            amount.addAndGet(item.getAmount().multiply(new BigDecimal(item.getNumber())).intValue());
            return orderDetail;
        }).collect(Collectors.toList());


        orders.setId(orderId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setCheckoutTime(LocalDateTime.now());
        orders.setStatus(2);
        orders.setAmount(new BigDecimal(amount.get()));//总金额
        orders.setUserId(userId);
        orders.setNumber(String.valueOf(orderId));
        orders.setUserName(user.getName());
        orders.setConsignee(addressBook.getConsignee());
        orders.setPhone(addressBook.getPhone());
        orders.setAddress((addressBook.getProvinceName() == null ? "" : addressBook.getProvinceName())
                + (addressBook.getCityName() == null ? "" : addressBook.getCityName())
                + (addressBook.getDistrictName() == null ? "" : addressBook.getDistrictName())
                + (addressBook.getDetail() == null ? "" : addressBook.getDetail()));
        //向订单表插入数据，一条数据
        this.save(orders);

        //向订单明细表插入数据，多条数据
        orderDetailService.saveBatch(orderDetails);

        //清空购物车数据
        shoppingCartService.remove(wrapper);
    }
    /**
     * 用户再次下单
     * @param orders
     */
    @Override
    public void again(Orders orders) {
        Long id = orders.getId();

        log.info("订单数据:{}",id);

        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Orders::getId,id);

        //得到原先的订单信息
        orders = orderService.getOne(queryWrapper);

        //获取订单明细表中的具体菜品
        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId,orders.getId());

        List<OrderDetail> list = orderDetailService.list(wrapper);

        list.stream().map((item) -> {
            Long dishId = item.getDishId();
            ShoppingCart shoppingCart = new ShoppingCart();

            //先设置用户id于购物车对象中
            Long currentId = BaseContext.getCurrentId();
            shoppingCart.setUserId(currentId);

            if(dishId != null){
                //从数据库中取出对应的菜品对象,设置到对应的购物车对象中并保存
                DishDto dishDto = dishService.getByIdWithFlavor(dishId);
                String dishFlavor = item.getDishFlavor();
                shoppingCart.setImage(dishDto.getImage());
                shoppingCart.setName(dishDto.getName());
                shoppingCart.setDishId(dishDto.getId());
                shoppingCart.setDishFlavor(dishFlavor);
                BigDecimal price = dishDto.getPrice();
                shoppingCart.setAmount(price.divide(new BigDecimal(100)));
            }else {
                //没有菜品id则说明是套餐
                Long setMealId = item.getSetmealId();

                SetmealDto setmealDto = setmealService.getByIdWithDish(setMealId);
                BigDecimal price = setmealDto.getPrice();
                shoppingCart.setAmount(price.divide(new BigDecimal(100)));
                shoppingCart.setName(setmealDto.getName());
                shoppingCart.setSetmealId(setmealDto.getId());
                shoppingCart.setImage(setmealDto.getImage());
            }

            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCart.setNumber(item.getNumber());
            shoppingCartService.save(shoppingCart);
            return item;
        }).collect(Collectors.toList());

        orderService.submit(orders);
    }
}