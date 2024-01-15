package com.hjw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hjw.common.ErrorCode;
import com.hjw.exception.BusinessException;
import com.hjw.mapper.UserMapper;
import com.hjw.model.domain.User;
import com.hjw.service.UserService;
import com.hjw.utils.AlgorithmUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.hjw.contant.UserConstant.ADMIN_ROLE;
import static com.hjw.contant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService
{

    @Resource
    private UserMapper userMapper;
    @Resource
    private HttpServletRequest request;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "yupi";

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @param planetCode    星球编号
     * @return 新用户 id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword, String planetCode)
    {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (planetCode.length() > 5)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球编号过长");
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find())
        {
            return -1;
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword))
        {
            return -1;
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 星球编号不能重复
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("planet_code", planetCode);
        count = userMapper.selectCount(queryWrapper);
        if (count > 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编号重复");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setPlanetCode(planetCode);
        boolean saveResult = this.save(user);
        if (!saveResult)
        {
            return -1;
        }
        return user.getId();
    }


    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request)
    {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword))
        {
            return null;
        }
        if (userAccount.length() < 4)
        {
            return null;
        }
        if (userPassword.length() < 8)
        {
            return null;
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find())
        {
            return null;
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);
        queryWrapper.eq(User::getUserPassword, encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null)
        {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 3. 用户脱敏
        User safetyUser = getSafetyUser(user);
        // 4. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, safetyUser);
        return safetyUser;
    }

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    @Override
    public User getSafetyUser(User originUser)
    {
        if (originUser == null)
        {
            return null;
        }
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setPlanetCode(originUser.getPlanetCode());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setTags(originUser.getTags());
        return safetyUser;
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request)
    {
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
    }

    /**
     * 根据标签搜索用户 BY java（灵活）  内存过滤
     *
     * @param tagNameList 用户拥有的标签
     * @return
     */
    public List<User> searchUserByTags(List<String> tagNameList)
    {
        if (CollectionUtil.isEmpty(tagNameList))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 查询出所有用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        List<User> javauserList = userMapper.selectList(queryWrapper);
        // 在内存中处理
        Gson gson = new Gson();
        // 用户集合筛选
        List<User> collectUserList = javauserList.stream().filter(user ->
        {
            // 该用户的所有标签
            String tags = user.getTags();
            if (StrUtil.isBlank(tags))  // 如果该用户无标签
            {
                return false;
            }
            // 标签集合 string
            Set<String> tagSet = gson.fromJson(tags, new TypeToken<Set<String>>()
            {
            }.getType());
            // Tag tag = JSONUtil.toBean(tags, List.class);

            // 遍历用户传递的 标签集合
            for (String s : tagNameList)
            {
                // 一旦不存在该标签，就过滤掉
                if (!tagSet.contains(s))
                {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());


        // 转换数据为 safety
        return collectUserList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }


    /**
     * 根据标签搜索用户 BY SQL （方便）
     *
     * @param tagNameList 用户拥有的标签
     * @return
     */
    @Deprecated  // 不用
    private List<User> searchUserByTagsBySQL(List<String> tagNameList)
    {
        if (CollectionUtil.isEmpty(tagNameList))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }


        // 方法一：sql拼接
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 拼接 模糊查询
        for (String tagName : tagNameList)
        {
            wrapper.like(User::getTags, tagName);
        }

        List<User> userList = userMapper.selectList(wrapper);
        return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());

    }

    public User getCurrentUser(HttpServletRequest request)
    {
        if (request == null)
        {
            return null;
        }
        User currentUser = (User) request.getSession().getAttribute(USER_LOGIN_STATE);

        if (currentUser == null)
        {
            throw new BusinessException(ErrorCode.NO_AUTH);

        }
        return currentUser;

    }

    /**
     * 判断当前用户是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request)
    {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == ADMIN_ROLE;
    }

    /**
     * 判断传入的用户是否为管理员
     *
     * @param currentUser
     * @return
     */
    public boolean isAdmin(User currentUser)
    {
        return currentUser != null && currentUser.getUserRole() == ADMIN_ROLE;
    }

    @Override
    public Integer updateUserById(User user, User currentUser)
    {
        long userId = user.getId();
        if (userId <= 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 如果用户传递的信息中只有 id，则不触发更新，直接返回
        Map<String, Object> map = BeanUtil.beanToMap(user, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
        );
        // 仅仅只有 id 一个属性
        if (map.size() == 1)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 1.权限校验(仅管理元 和 当前用户 可以修改)
        if (!isAdmin(currentUser) && userId != currentUser.getId())
        {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        // 查询用户是否存在
        User oldUser = userMapper.selectById(userId);
        if (oldUser == null)
        {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return userMapper.updateById(user);

    }

    @Override
    public List<User> matchUsers(long num)
    {
        // 获取当前用户的 标签list
        User currentUser = this.getCurrentUser(request);
        String tags = currentUser.getTags();
        Gson gson = new Gson();
        List<String> currentTagList = gson.fromJson(tags, new TypeToken<List<String>>()
        {
        }.getType());

        // 获取所有用户
        // (优化)：过滤掉标签为空的用户、只查需要的信息
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(User::getTags);
        wrapper.select(User::getId, User::getTags);
        List<User> userList = this.list(wrapper);
        // 用户列表下表 : 相似度权重
        ArrayList<Pair<User, Long>> list = new ArrayList<>();
        long max = 0;
        for (int i = 0; i < userList.size(); i++)
        {

            // 依次获取用户，并取出tags，转换为 taglist
            User user = userList.get(i);
            String userTags = user.getTags();
            if (StrUtil.isBlank(userTags) || user.getId().equals(currentUser.getId()))
            {
                continue;
            }
            List<String> userTagList = gson.fromJson(userTags, new TypeToken<List<String>>()
            {
            }.getType());

            // 进行相似度匹配，计算出权重
            long distance = AlgorithmUtils.minDistance(currentTagList, userTagList);

            // 添加进map
            list.add(new Pair<>(user, distance));

        }

        // 取出前五个 pair
        // 原生的sort性能更佳
        list.sort((o1, o2) -> (int) (o1.getValue() - o2.getValue()));
        List<Pair<User, Long>> topPairList = list.stream().limit(num).collect(Collectors.toList());

        // 取出前五个 user，获取用户的完整信息，再脱敏
        ArrayList<User> topUserList = new ArrayList<>();
        for (Pair<User, Long> userLongPair : topPairList)
        {
            User user = userLongPair.getKey();
            User fullUser = this.getById(user);
            User safetyUser = this.getSafetyUser(fullUser);
            topUserList.add(safetyUser);
        }

        return topUserList;
    }
}

