package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.reggie.common.R;
import com.itheima.reggie.entity.User;
import com.itheima.reggie.service.UserService;
import com.itheima.reggie.utils.SMSUtils;
import com.itheima.reggie.utils.ValidateCodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * 移动端用户管理
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {
    @Resource
    private UserService userService;
    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 用户登入
     * @param map
     * @param httpSession
     * @return
     */
    @PostMapping("/login")
    public R<String> login(@RequestBody  Map map,HttpSession httpSession) {
        log.info("phone:"+map);
        //获取手机号
        String phone = map.get("phone").toString();
        //获取验证码
        String code = map.get("code").toString();

        if(StringUtils.isEmpty(phone) || StringUtils.isEmpty(code)){
            return R.error("登入失败");
        }

        // 【1】旧版本(优化前)
        // Object codeSeesion = httpSession.getAttribute(phone);

        // 【2】 优化后
        Object codeSeesion = redisTemplate.opsForValue().get(phone);
        //进行验证码的比对（页面提交的验证码和Session中保存的验证码比对）
        if (codeSeesion != null && codeSeesion.equals(code)) {
            // 表示验证无误,验证用户是否是第一次登入
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getPhone, phone);
            User user_temp = userService.getOne(queryWrapper);
            if (user_temp == null) {
                // 表示当前用户不存在,并创建用户
                user_temp = new User();
                user_temp.setPhone(phone);
                user_temp.setStatus(1);
                userService.save(user_temp);
            }
            // 登入成功,将用户数据写入到httpSession
            httpSession.setAttribute("user",user_temp.getId());
            //如果用户登录成功，删除Redis中缓存的验证码
            redisTemplate.delete(phone);
            return R.success("登入成功");
        }
        return R.error("登入失败");
    }



    /**
     * 发送手机短信验证码
     * @param user
     * @return
     */
    @PostMapping("/sendMsg")
    public R<String> sendMsg(@RequestBody User user, HttpSession session){
        //获取手机号
        String phone = user.getPhone();
        // 验证手机号是否为空
        if(StringUtils.isNotEmpty(phone)){
            //生成随机的4位验证码
            String code = ValidateCodeUtils.generateValidateCode(4).toString();
            log.info("code={}",code);

            //调用阿里云提供的短信服务API完成发送短信
            //SMSUtils.sendMessage("瑞吉外卖","",phone,code);

            // 【1】旧版本(优化前)
            //需要将生成的验证码保存到Session
            // session.setAttribute(phone,code);

            // 【2】 优化后
            // 将验证码保存到Redis中,保存时长5分钟
            redisTemplate.opsForValue().set(phone,code,5, TimeUnit.MINUTES);

            return R.success(code);
        }

        return R.error("短信发送失败");
    }

    @PostMapping("/loginout")
    public R<String> loginout(HttpSession httpSession) {
        // httpSession销毁
        httpSession.removeAttribute("user");
        return R.success("退出成功");
    }
}
