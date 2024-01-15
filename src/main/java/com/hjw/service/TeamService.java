package com.hjw.service;

import com.hjw.model.domain.Team;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hjw.model.dto.TeamUserDto;
import com.hjw.model.request.TeamJoinRequest;
import com.hjw.model.request.TeamQueryRequest;
import com.hjw.model.request.TeamQuitRequest;
import com.hjw.model.request.TeamUpdateRequest;

import java.util.List;

/**
* @author 86157
* @description 针对表【team(队伍)】的数据库操作Service
* @createDate 2024-01-11 14:52:00
*/
public interface TeamService extends IService<Team> {

    Long addTeam(Team team);

    List<TeamUserDto> listTeams(TeamQueryRequest teamQuery);

    boolean updateTeam(TeamUpdateRequest teamUpdateRequest);

    boolean joinTeam(TeamJoinRequest teamJoinRequest);

    boolean quitTeam(TeamQuitRequest teamQuitRequest);

    boolean deleteTeam(long id);
}
