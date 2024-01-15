package com.hjw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hjw.common.BaseResponse;
import com.hjw.model.domain.User;
import com.hjw.model.dto.UserDto;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.hjw.contant.UserConstant.ADMIN_ROLE;
import static com.hjw.contant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务
 *
 */
public interface UserService extends IService<User>
{

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @param planetCode    星球编号
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword, String planetCode);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    User getSafetyUser(User originUser);


    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    int userLogout(HttpServletRequest request);


    /**
     * 根据标签搜索用户
     *
     * @param tagNameList 用户拥有的标签
     * @return
     */
    public List<User> searchUserByTags(List<String> tagNameList);

    /**
     * 更新用户信息
     *
     * @param user
     * @return
     */
    public Integer updateUserById(User user, User currentUser);


    /**
     * 获取当前登陆用户信息
     *
     * @return
     */
    public User getCurrentUser(HttpServletRequest request);


    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    public boolean isAdmin(HttpServletRequest request);
    public boolean isAdmin(User currentUser);


    List<User> matchUsers(long num);
}
