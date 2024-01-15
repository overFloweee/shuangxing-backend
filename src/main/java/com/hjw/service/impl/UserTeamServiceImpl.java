package com.hjw.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hjw.model.domain.UserTeam;
import com.hjw.service.UserTeamService;
import com.hjw.mapper.UserTeamMapper;
import org.springframework.stereotype.Service;

/**
* @author 86157
* @description 针对表【user_team(用户队伍关系)】的数据库操作Service实现
* @createDate 2024-01-11 14:52:20
*/
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
    implements UserTeamService{

}




