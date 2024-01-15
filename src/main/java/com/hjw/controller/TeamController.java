package com.hjw.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjw.common.BaseResponse;
import com.hjw.common.ErrorCode;
import com.hjw.common.ResultUtils;
import com.hjw.exception.BusinessException;
import com.hjw.model.domain.Team;
import com.hjw.model.domain.User;
import com.hjw.model.domain.UserTeam;
import com.hjw.model.dto.TeamUserDto;
import com.hjw.model.request.*;
import com.hjw.service.TeamService;
import com.hjw.service.UserService;
import com.hjw.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 队伍接口
 */
@RestController
@RequestMapping("/team")
@Slf4j
public class TeamController
{

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;


    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest)
    {
        if (teamAddRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Team team = BeanUtil.copyProperties(teamAddRequest, Team.class);
        Long teamId = teamService.addTeam(team);
        return ResultUtils.success(teamId);
    }


    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest)
    {
        if (teamUpdateRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        boolean isUpdate = teamService.updateTeam(teamUpdateRequest);
        if (!isUpdate)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败！");
        }
        return ResultUtils.success(true);
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest)
    {
        if (teamJoinRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        boolean isJoin = teamService.joinTeam(teamJoinRequest);
        if (!isJoin)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败！");
        }
        return ResultUtils.success(true);
    }

    // 退出队伍
    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest)
    {
        if (teamQuitRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        boolean isQuit = teamService.quitTeam(teamQuitRequest);
        if (!isQuit)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败！");
        }
        return ResultUtils.success(true);
    }

    // 解散队伍
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody TeamDeleteRequest teamDeleteRequest)
    {
        if (teamDeleteRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamDeleteRequest.getId();

        boolean remove = teamService.deleteTeam(teamId);
        if (!remove)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败！");
        }
        return ResultUtils.success(true);
    }


    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(long id)
    {
        if (id <= 0)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Team teamData = teamService.getById(id);

        if (teamData == null)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败！");
        }
        return ResultUtils.success(teamData);
    }

    @GetMapping("/list")
    public BaseResponse<List<TeamUserDto>> listTeams(TeamQueryRequest teamQuery, HttpServletRequest request)
    {
        if (teamQuery == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 符合查询条件 的队伍集合
        List<TeamUserDto> teamList = teamService.listTeams(teamQuery);

        // 获取 队伍id集合
        List<Long> teamIdList = teamList.stream().map(TeamUserDto::getId).collect(Collectors.toList());
        LambdaQueryWrapper<UserTeam> wrapper = new LambdaQueryWrapper<>();


        Set<Long> currentUserJoinTeamIdSet = new HashSet<>();
        try
        {
            // 获取当前用户 加入的 原先队伍集合
            User currentUser = userService.getCurrentUser(request);
            wrapper.eq(UserTeam::getUserId, currentUser.getId());
            wrapper.in(UserTeam::getTeamId, teamIdList);
            List<UserTeam> currentUserJoinTeamList = userTeamService.list(wrapper);
            // 用户加入队伍的集合
            currentUserJoinTeamIdSet = currentUserJoinTeamList.stream().map(UserTeam::getTeamId)
                                                              .collect(Collectors.toSet());

        }
        catch (Exception e)
        {
            log.error("");
        }

        // 查询加入队伍的人数
        wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserTeam::getTeamId, teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(wrapper);
        // teamId  => 加入这个队伍的 列表
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream()
                                                                   .collect(Collectors.groupingBy(UserTeam::getTeamId));


        // 遍历 原先队伍集合
        for (TeamUserDto team : teamList)
        {
            // 如果 当前team，存在与 用户加入队伍的set，则 已加入队伍
            boolean hasJoin = currentUserJoinTeamIdSet.contains(team.getId());
            team.setHasJoin(hasJoin);

            // 设置加入队伍人数
            Long teamId = team.getId();
            int size = teamIdUserTeamList.getOrDefault(teamId, new ArrayList<>()).size();
            team.setHasJoinNum(size);
        }
        return ResultUtils.success(teamList);
    }


    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQueryRequest teamQuery)
    {
        if (teamQuery == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Page<Team> teamPage = new Page<>();
        teamPage.setCurrent(teamQuery.getPageNum());
        teamPage.setSize(teamQuery.getPageSize());
        // 封装 实体类
        Team team = BeanUtil.copyProperties(teamQuery, Team.class);
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<>(team);
        teamPage = teamService.page(teamPage, wrapper);

        if (teamPage == null)
        {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败！");
        }
        return ResultUtils.success(teamPage);
    }


    /**
     * 获取我创建的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserDto>> listTeamsMycreate(TeamQueryRequest teamQuery, HttpServletRequest request)
    {
        if (teamQuery == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long userId = userService.getCurrentUser(request).getId();
        // 添加一个查询条件，userId = currentUser.id，自己是创建人
        teamQuery.setUserId(userId);
        List<TeamUserDto> teamUserDtoList = teamService.listTeams(teamQuery);

        return ResultUtils.success(teamUserDtoList);
    }


    /**
     * 获取我加入的队伍
     *
     * @param teamQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserDto>> listTeamsMyJoin(TeamQueryRequest teamQueryRequest, HttpServletRequest request
    )
    {
        if (teamQueryRequest == null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 获取当前我加入的队伍id 列表
        Long currentUserId = userService.getCurrentUser(request).getId();
        LambdaQueryWrapper<UserTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTeam::getUserId, currentUserId);
        List<UserTeam> list = userTeamService.list(wrapper);

        ArrayList<Long> idList = new ArrayList<>();
        for (UserTeam userTeam : list)
        {
            Long teamId = userTeam.getTeamId();
            idList.add(teamId);
        }
        // 新增查询条件
        teamQueryRequest.setIdList(idList);

        List<TeamUserDto> teamUserDtoList = teamService.listTeams(teamQueryRequest);

        return ResultUtils.success(teamUserDtoList);
    }


}
