package com.atwj.aiyou.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atwj.aiyou.model.domain.UserTeam;
import com.atwj.aiyou.service.UserTeamService;
import com.atwj.aiyou.mapper.UserTeamMapper;
import org.springframework.stereotype.Service;

/**
* @author blablablala
* @description 针对表【user_team(用户队伍关系)】的数据库操作Service实现
* @createDate 2023-05-11 18:25:48
*/
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
    implements UserTeamService{

}




