package com.hjw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.intern.InternUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hjw.common.ErrorCode;
import com.hjw.exception.BusinessException;
import com.hjw.model.domain.Team;
import com.hjw.model.domain.User;
import com.hjw.model.domain.UserTeam;
import com.hjw.model.dto.TeamUserDto;
import com.hjw.model.dto.UserDto;
import com.hjw.model.enums.TeamStatusEnum;
import com.hjw.model.request.TeamJoinRequest;
import com.hjw.model.request.TeamQueryRequest;
import com.hjw.model.request.TeamQuitRequest;
import com.hjw.model.request.TeamUpdateRequest;
import com.hjw.service.TeamService;
import com.hjw.mapper.TeamMapper;
import com.hjw.service.UserService;
import com.hjw.service.UserTeamService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author 86157
 * @description 针对表【team(队伍)】的数据库操作Service实现
 * @createDate 2024-01-11 14:52:00
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService
{


    @Resource
    private UserService userService;

    @Resource
    private HttpServletRequest request;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private RedissonClient redissonClient;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addTeam(Team team)
    {
        if (team == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 1.用户是否登陆
        User currentUser = userService.getCurrentUser(request);
        if (currentUser == null)
        {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        long userId = currentUser.getId();
        team.setUserId(userId);

        // 2.校验信息
        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数不满足要求！");
        }
        String name = team.getName();
        if (StrUtil.isBlank(name) || name.length() > 20)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍名称不符合要求！");
        }
        String description = team.getDescription();
        if (StrUtil.isNotBlank(description) && description.length() > 512)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述不符合要求！");
        }
        // status 状态是否为 公开，不传默认为0
        int status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不符合要求！");
        }

        // 如果status是加密状态，则一定要密码，且密码 <= 32 位
        String password = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum))
        {
            if (StrUtil.isBlank(password) || password.length() > 32)
            {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍密码设置不符合要求！");
            }
        }
        // 如果status 是 非加密状态，则不需要密码
        else
        {
            team.setPassword("");
        }
        // 传入的超时时间 > 当前时间
        if (new Date().after(team.getExpireTime()))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "传入的超时时间 大于 当前时间");
        }

        // 校验用户最多 5个队伍
        // todo 并发问题
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Team::getUserId, userId);
        long count = this.count(wrapper);
        if (count >= 5)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户最多创建5支队伍");
        }


        // 插入队伍信息到数据库
        boolean save = this.save(team);
        if (!save)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        // 插入 用户队伍 关联表
        UserTeam userTeam = BeanUtil.copyProperties(team, UserTeam.class, "id");
        userTeam.setTeamId(team.getId());
        userTeam.setJoinTime(new Date());
        boolean isInsert = userTeamService.save(userTeam);
        if (!isInsert)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }

        return team.getId();

    }


    @Override
    public List<TeamUserDto> listTeams(TeamQueryRequest teamQuery)
    {
        // 判断是否是 管理员
        boolean isAdmin = userService.isAdmin(request);
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<>();


        if (BeanUtil.isNotEmpty(teamQuery, "pageSize", "pageNum"))
        {
            // 如果传入的查询条件不为空，则 添加查询条件
            Long id = teamQuery.getId();
            String name = teamQuery.getName();
            String description = teamQuery.getDescription();
            Integer maxNum = teamQuery.getMaxNum();
            Long userId = teamQuery.getUserId();
            Integer status = teamQuery.getStatus();
            String searchText = teamQuery.getSearchText();

            wrapper.eq(id != null && id > 0, Team::getId, id);
            wrapper.like(StrUtil.isNotBlank(name), Team::getName, name);
            wrapper.like(StrUtil.isNotBlank(description), Team::getDescription, description);
            wrapper.eq(maxNum != null && maxNum > 0, Team::getMaxNum, maxNum);
            wrapper.eq(userId != null && userId > 0, Team::getUserId, userId);


            // 根据状态来查询
            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
            // 只有自己和管理员才能查看自己加入的全部队伍，包括公开、私有、加密
            if (!isAdmin && TeamStatusEnum.PRIVATE.equals(statusEnum))
            {
                throw new BusinessException(ErrorCode.NO_AUTH);
            }
            // 未传参数，则默认查看所有
            wrapper.eq(status != null, Team::getStatus, status);

            // 搜索文本 模糊搜索
            wrapper.and(StrUtil.isNotBlank(searchText),
                    text -> text.like(Team::getName, searchText).or().like(Team::getDescription, searchText)
            );

            // 根据id List查询
            List<Long> idList = teamQuery.getIdList();
            wrapper.in(CollectionUtil.isNotEmpty(idList), Team::getId, idList);

        }

        // 不展示已 过期队伍的信息
        wrapper.gt(Team::getExpireTime, new Date());

        // 查询出符合查询条件的 队伍集合
        List<Team> teamList = this.list(wrapper);

        if (CollectionUtil.isEmpty(teamList))
        {
            return new ArrayList<>();
        }

        // 关联查询 创建人的信息
        return teamList.stream().map(team ->
        {
            Long userId = team.getUserId();
            // 封装类 转换
            TeamUserDto teamUserDto = BeanUtil.copyProperties(team, TeamUserDto.class);
            User user = userService.getById(userId);
            UserDto userDto = BeanUtil.copyProperties(user, UserDto.class);
            teamUserDto.setCreateUser(userDto);

            return teamUserDto;
        }).collect(Collectors.toList());
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest)
    {
        // 判断是否是 管理员
        boolean isAdmin = userService.isAdmin(request);
        if (teamUpdateRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long teamId = teamUpdateRequest.getId();
        if (teamId == null || teamId <= 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String name = teamUpdateRequest.getName();
        if (StrUtil.isBlank(name))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍名称不符合要求！");
        }

        String password = teamUpdateRequest.getPassword();
        Integer status = teamUpdateRequest.getStatus();
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        // 修改状态为 未加密，但是 传入了密码，则置空
        if (!TeamStatusEnum.SECRET.equals(statusEnum) && StrUtil.isNotBlank(password))
        {
            teamUpdateRequest.setPassword("");
        }
        // 修改状态为加密，但是没有 传入密码
        if (TeamStatusEnum.SECRET.equals(statusEnum) && StrUtil.isBlank(password))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "加密房间未设置密码！");
        }

        // 判断旧数据是否存在
        Team oldTeam = this.getById(teamId);
        if (oldTeam == null)
        {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        // 判断修改的用户 是否是 创建人 或者是 管理员  !(createUser || Admin)
        User currentUser = userService.getCurrentUser(request);
        if (!currentUser.getId().equals(oldTeam.getUserId()) && !isAdmin)
        {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }


        Team updateTeam = BeanUtil.copyProperties(teamUpdateRequest, Team.class);
        return this.updateById(updateTeam);
    }


    /**
     * 加入队伍
     *
     * @param teamJoinRequest
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean joinTeam(TeamJoinRequest teamJoinRequest)
    {

        if (teamJoinRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User currentUser = userService.getCurrentUser(request);
        Long userId = currentUser.getId();


        // 判断 该队伍是否存在
        Long teamId = teamJoinRequest.getTeamId();
        Team team = getTeamById(teamId);

        // 只能加入 未满、未过期、不为私有的队伍
        // 校验是否过期
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date()))
        {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍已过期！");
        }
        Integer status = team.getStatus();
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (TeamStatusEnum.PRIVATE.equals(statusEnum))
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入私有队伍！");
        }
        // 校验密码
        String password = team.getPassword();
        String requestPassword = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum))
        {
            if (StrUtil.isBlank(requestPassword) || !password.equals(requestPassword))
            {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误！");
            }
        }

        boolean isSave = false;
        // 加锁
        // todo 分段锁
        RLock lock = redissonClient.getLock("shuangxing:join_team");
        try
        {
            if (lock.tryLock(-1, -1, TimeUnit.MILLISECONDS))
            {

                // 判断当前用户 加入队伍的数量
                LambdaQueryWrapper<UserTeam> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(userId != null, UserTeam::getUserId, userId);
                long hasJoinNum = userTeamService.count(queryWrapper);
                if (hasJoinNum >= 5)
                {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多加入五个队伍!");
                }


                // 校验队伍内人数是否已满
                long teamHasJoinNum = getTeamHasJoinNumByTeamId(teamId);
                if (teamHasJoinNum >= team.getMaxNum())
                {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满！");
                }

                // 不能重复加入该队伍
                LambdaQueryWrapper<UserTeam> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(UserTeam::getTeamId, teamId);
                wrapper.eq(UserTeam::getUserId, userId);
                long count = userTeamService.count(wrapper);
                if (count >= 1)
                {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能重复加入该队伍！");
                }


                // 修改队伍信息 到 数据库
                UserTeam userTeam = new UserTeam();
                userTeam.setUserId(userId);
                userTeam.setTeamId(teamId);
                userTeam.setJoinTime(new Date());
                isSave = userTeamService.save(userTeam);
            }


        }
        catch (InterruptedException e)
        {
            log.error("");
        }
        finally
        {
            // 只能是否自己线程的锁
            if (lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }
        }

        return isSave;

    }


    /**
     * 判断队伍是否存在，根据id
     *
     * @param teamId
     * @return
     */
    private Team getTeamById(Long teamId)
    {
        if (teamId == null || teamId <= 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null)
        {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在！");
        }
        return team;
    }

    private long getTeamHasJoinNumByTeamId(Long teamId)
    {
        LambdaQueryWrapper<UserTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTeam::getTeamId, teamId);
        return userTeamService.count(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean quitTeam(TeamQuitRequest teamQuitRequest)
    {
        if (teamQuitRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long teamId = teamQuitRequest.getTeamId();
        if (teamId == null || teamId <= 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User currentUser = userService.getCurrentUser(request);
        Long userId = currentUser.getId();

        // 校验队伍是否存在
        Team team = this.getTeamById(teamId);

        // 校验是否加入队伍
        LambdaQueryWrapper<UserTeam> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTeam::getTeamId, teamId);
        queryWrapper.eq(UserTeam::getUserId, userId);
        long hasJoin = userTeamService.count(queryWrapper);
        if (hasJoin <= 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未加入该队伍！");
        }

        // 查询队伍人数，如果队伍只剩一个人
        long hasJoinNum = getTeamHasJoinNumByTeamId(teamId);
        if (hasJoinNum == 1)
        {
            // 解散
            userTeamService.remove(queryWrapper);
            return this.removeById(teamId);
        }

        // 队伍剩余不只一人
        Long createUserId = team.getUserId();

        //     是队长
        if (userId.equals(createUserId))
        {
            //     则将 队长换为 第二个最早加入队伍的人，即id第二小
            LambdaQueryWrapper<UserTeam> nextQueryWrapper = new LambdaQueryWrapper<>();
            nextQueryWrapper.eq(UserTeam::getTeamId, teamId);
            // 拼接sql语句到最后，只用 取出两条数据
            nextQueryWrapper.last("order by id asc limit 2");
            List<UserTeam> list = userTeamService.list(nextQueryWrapper);
            if (CollectionUtil.isEmpty(list) || list.size() <= 1)
            {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            UserTeam nextUserTeam = list.get(1);
            Long nextTeamUserid = nextUserTeam.getUserId();
            // 更新为队长id
            Team updateTeam = new Team();
            updateTeam.setUserId(nextTeamUserid);
            updateTeam.setId(teamId);
            boolean isUpdate = this.updateById(updateTeam);
            if (!isUpdate)
            {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新队长失败！");
            }
        }

        // 是否是队长 都需要删除  用户与队伍的关联关系
        return userTeamService.remove(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteTeam(long id)
    {
        // 校验队伍是否存在
        Team team = getTeamById(id);
        // 校验是否为队长
        User currentUser = userService.getCurrentUser(request);
        Long userId = currentUser.getId();

        if (!userId.equals(team.getUserId()))
        {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        // 移除 队伍信息
        boolean isDelete = this.removeById(id);
        if (!isDelete)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解散队伍失败！");
        }
        // 移除加入队伍的关联信息
        LambdaQueryWrapper<UserTeam> userTeamWrapper = new LambdaQueryWrapper<>();
        userTeamWrapper.eq(UserTeam::getTeamId, id);
        boolean remove = userTeamService.remove(userTeamWrapper);
        if (!remove)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍关联关系失败！");
        }

        return true;


    }
}




