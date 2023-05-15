package com.atwj.aiyou.service;

import com.atwj.aiyou.model.domain.Team;
import com.atwj.aiyou.model.domain.User;
import com.atwj.aiyou.model.dto.TeamQuery;
import com.atwj.aiyou.model.request.TeamJoinRequest;
import com.atwj.aiyou.model.request.TeamQuitRequest;
import com.atwj.aiyou.model.request.TeamUpdateRequest;
import com.atwj.aiyou.model.vo.TeamUserVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author blablablala
* @description 针对表【team(队伍)】的数据库操作Service
* @createDate 2023-05-11 18:23:18
*/
public interface TeamService extends IService<Team> {

    /**
     * 增添队伍
     * @param loginUser
     * @param team
     * @return
     */
    long addTeam(User loginUser, Team team);

    /**
     * 查询队伍集合
     *
     * @param teamQuery
     * @param isAdmin
     * @return
     */
    List<TeamUserVo> listTeams(TeamQuery teamQuery, boolean isAdmin);

    /**
     * 更新队伍信息
     * @param teamUpdateRequest
     * @param loginUser
     * @return
     */
    boolean updateTeamInfo(TeamUpdateRequest teamUpdateRequest, User loginUser);

    /**
     * 加入队伍
     * @param teamJoinRequest
     * @param loginUser
     * @return
     */
    boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser);

    /**
     * 退出队伍
     * @param teamQuitRequest
     * @param loginUser
     * @return
     */
    boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser);

    /**
     * 解散队伍
     * @param teamId
     * @param loginUser
     * @return
     */
    boolean deleteTeam(long teamId, User loginUser);
}
