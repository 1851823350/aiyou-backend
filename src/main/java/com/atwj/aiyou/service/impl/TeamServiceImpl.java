package com.atwj.aiyou.service.impl;


import com.atwj.aiyou.common.ErrorCode;
import com.atwj.aiyou.exception.BusinessException;
import com.atwj.aiyou.model.domain.User;
import com.atwj.aiyou.model.domain.UserTeam;
import com.atwj.aiyou.model.dto.TeamQuery;
import com.atwj.aiyou.model.enums.TeamStatusEnum;
import com.atwj.aiyou.model.request.TeamJoinRequest;
import com.atwj.aiyou.model.request.TeamQuitRequest;
import com.atwj.aiyou.model.request.TeamUpdateRequest;
import com.atwj.aiyou.model.vo.TeamUserVo;
import com.atwj.aiyou.model.vo.UserVO;
import com.atwj.aiyou.service.UserService;
import com.atwj.aiyou.service.UserTeamService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atwj.aiyou.model.domain.Team;
import com.atwj.aiyou.service.TeamService;
import com.atwj.aiyou.mapper.TeamMapper;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author blablablala
 * @description 针对表【team(队伍)】的数据库操作Service实现
 * @createDate 2023-05-11 18:23:18
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    @Resource
    private UserService userService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(User loginUser, Team team) {
        //1. 请求参数是否为空？
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "添加队伍信息为空");
        }
        //2. 是否登录，未登录不允许创建
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未登录，请登录");
        }
        //3. 校验信息
        //队伍人数 > 1 且 <= 20
        /**
         * Optional.ofNullable(team.getMaxNum()): 这个表达式首先调用了 Optional.ofNullable 方法，将 team.getMaxNum() 的返回值包装到
         * 一个 Optional 对象中。ofNullable 方法允许传入一个可能为 null 的值，如果传入的值为 null，则返回一个空的 Optional 对象；
         * 否则，返回一个包含传入值的 Optional 对象。
         * .orElse(0): 在调用 Optional.ofNullable(team.getMaxNum()) 之后，使用 .orElse(0) 方法来指定一个默认值。如果 Optional 对象中
         * 的值存在（即不为 null），则返回该值；如果 Optional 对象为空（即值为 null），则返回指定的默认值，这里是 0。
         */
        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数超过最大值");
        }
        //队伍标题 <= 20
        String teamName = team.getName();
        if (StringUtils.isBlank(teamName) || teamName.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍名称不满足要求，请修改");
        }
        //描述 <= 512
        String description = team.getDescription();
        if (StringUtils.isBlank(description) || description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述不满足要求，请修改");
        }
        //status 是否公开（int）不传默认为 0（公开）
        Integer teamStatus = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum enumByValue = TeamStatusEnum.getEnumByValue(teamStatus);
        if (enumByValue == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不满足要求，请重新修改");
        }
        //如果 status 是加密状态，一定要有密码，且密码 <= 32
        if (TeamStatusEnum.SECRET.equals(enumByValue)) {
            if (StringUtils.isBlank(team.getPassword()) || team.getPassword().length() > 32) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍密码过长");
            }
        }
        //9. 超时时间 > 当前时间
        Date expireTime = team.getExpireTime();
        if (expireTime != null) {
            if (new Date().after(expireTime)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "超时时间 > 当前时间");
            }
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "超时时间不能为空");
        }

        //10. 校验用户最多创建 5 个队伍
        final Long userId = loginUser.getId();
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        long teamCount = this.count(queryWrapper);
        if (teamCount + 1 > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "您创建的队伍数量以达到最大值");
        }
        //11. 插入队伍信息到队伍表
        team.setId(null);
        team.setUserId(userId);
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        //12. 插入用户 => 队伍关系到关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        return teamId;
    }

    @Override
    public List<TeamUserVo> listTeams(TeamQuery teamQuery, boolean isAdmin) {
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        //组合条件
        if (teamQuery != null) {
            String teamName = teamQuery.getName();
            if (StringUtils.isNotBlank(teamName)) {
                queryWrapper.eq("name", teamName);
            }

            Long id = teamQuery.getId();
            if (id != null && id > 0) {
                queryWrapper.eq("id", id);
            }

            List<Long> idList = teamQuery.getIdList();
            if (!CollectionUtils.isEmpty(idList)) {
                queryWrapper.in("id", idList);
            }

            String searchText = teamQuery.getSearchText();
            if (StringUtils.isNotBlank(searchText)) {
                queryWrapper.and(qw -> qw.like("name", searchText)).or().like("description", searchText);
            }

            String description = teamQuery.getDescription();
            if (StringUtils.isNotBlank(description)) {
                queryWrapper.like("description", description);
            }

            Long userId = teamQuery.getUserId();
            if (userId != null && userId > 0) {
                queryWrapper.eq("userId", userId);
            }

            Integer maxNum = teamQuery.getMaxNum();
            if (maxNum != null && maxNum > 0) {
                queryWrapper.eq("maxNum", maxNum);
            }

            Integer status = teamQuery.getStatus();
            TeamStatusEnum enumByValue = TeamStatusEnum.getEnumByValue(status);
            if (enumByValue == null) {
                enumByValue = TeamStatusEnum.PUBLIC;
            }
            if (!isAdmin && enumByValue.equals(TeamStatusEnum.PRIVATE)) {
                throw new BusinessException(ErrorCode.NO_AUTH, "无权查看私密队伍");
            }
            queryWrapper.eq("status", enumByValue.getValue());
        }

        //不展示已过期的队伍信息
        //expireTime is null or expireTime > new Date()
        queryWrapper.and(qw -> qw.isNull("expireTime").or().gt("expireTime", new Date()));

        //查询队伍
        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }

        //关联查询创建者信息
        List<TeamUserVo> teamUserVoList = new ArrayList<>();
        for (Team team : teamList) {
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }
            User userInfo = userService.getById(userId);
            TeamUserVo teamUserVo = new TeamUserVo();
            BeanUtils.copyProperties(team, teamUserVo);
            //为用户信息脱敏
            if (userInfo != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(userInfo, userVO);
                teamUserVo.setCreateUser(userVO);
            }
            teamUserVoList.add(teamUserVo);
        }

        return teamUserVoList;
    }

    @Override
    public boolean updateTeamInfo(TeamUpdateRequest teamUpdateRequest, User loginUser) {
//        2. 查询队伍是否存在
        Long teamId = teamUpdateRequest.getId();
        if (teamId == null || teamId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍ID错误，请检查");
        }
        Team oldTeam = this.getById(teamId);
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在，请重新输入");
        }
//        3. 只有管理员或者队伍的创建者可以修改
        if (!oldTeam.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
//        4. 如果用户传入的新值和老值一致，就不用 update 了（可自行实现，降低数据库使用次数）
//        5. 如果队伍状态改为加密，必须要有密码
        TeamStatusEnum enumByValue = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if (enumByValue.equals(TeamStatusEnum.SECRET)) {
            if (StringUtils.isBlank(teamUpdateRequest.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请设置密码");
            }
        }

        // TODO: 2023/5/12 如果用户传输的未更新队伍的所有信息，需要保留未更改的信息
        if (StringUtils.isBlank(teamUpdateRequest.getDescription())) {
            teamUpdateRequest.setDescription(oldTeam.getDescription());
        }
//        6. 更新成功
        Team newTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, newTeam);
        return this.updateById(newTeam);
    }

    @Override
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamJoinRequest.getTeamId();
        Team team = getTeamById(teamId);
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已过期");
        }
        Integer status = team.getStatus();
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入私有队伍");
        }
        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !password.equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
            }
        }
        // 该用户已加入的队伍数量
        long userId = loginUser.getId();
        // 只有一个线程能获取到锁
        RLock lock = redissonClient.getLock("aiyou:join_team");
        try {
            // 抢到锁并执行
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    System.out.println("getLock: " + Thread.currentThread().getId());
                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
                    if (hasJoinNum > 5) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入 5 个队伍");
                    }
                    // 不能重复加入已加入的队伍
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    userTeamQueryWrapper.eq("teamId", teamId);
                    long hasUserJoinTeam = userTeamService.count(userTeamQueryWrapper);
                    if (hasUserJoinTeam > 0) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户已加入该队伍");
                    }
                    // 已加入队伍的人数
                    long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
                    if (teamHasJoinNum >= team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
                    }
                    // 修改队伍信息
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(userId);
                    userTeam.setTeamId(teamId);
                    userTeam.setJoinTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        } catch (InterruptedException e) {
            log.error("doCacheRecommendUser error", e);
            return false;
        } finally {
            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }
    }

    @Override
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long teamId = teamQuitRequest.getTeamId();
        Team team = this.getById(teamId);
        Long userId = loginUser.getId();

        //校验我是否已加入队伍
        UserTeam queryUerTeam = new UserTeam();
        queryUerTeam.setTeamId(teamId);
        queryUerTeam.setUserId(userId);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>(queryUerTeam);
        long count = userTeamService.count(queryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "你未加入该队伍，请核实信息");
        }

        long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
        if (teamHasJoinNum == 1) {
            //只剩一人，队伍解散
            this.removeById(teamId);
        } else {
            //队伍不止一人
            //1.如果退出的人为队长
            if (team.getUserId().equals(loginUser.getId())) {
                //需要将队长职务转交给当前剩下人数中加入时间最早的人
                //1.查询剩下人数中加入时间最早的人
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.eq("teamId", teamId);
                userTeamQueryWrapper.last("order by id asc limit 2");
                List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                if (CollectionUtils.isEmpty(userTeamList) || userTeamList.size() <= 1) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
                UserTeam userTeam = userTeamList.get(1);
                Long newTeamLeaderId = userTeam.getUserId();
                //2.更新队伍队长
                Team newTeam = new Team();
                newTeam.setUserId(newTeamLeaderId);
                newTeam.setId(teamId);
                boolean result = this.updateById(newTeam);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
            }
        }
        return userTeamService.remove(queryWrapper);
    }

    @Override
    public boolean deleteTeam(long teamId, User loginUser) {
        //校验队伍是否存在
        Team team = getTeamById(teamId);
        //校验你是不是队伍的队长
        Long teamLeaderId = team.getUserId();
        if (!teamLeaderId.equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无删除权限");
        }

        //移除所有队员关联信息
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        boolean result = userTeamService.remove(queryWrapper);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解散队伍失败");
        }
        //删除队伍信息
        return this.removeById(teamId);
    }

    /**
     * 获取某队伍当前人数
     *
     * @param teamId
     * @return
     */
    private long countTeamUserByTeamId(long teamId) {
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        return userTeamService.count(userTeamQueryWrapper);
    }

    /**
     * 根据 id 获取队伍信息
     *
     * @param teamId
     * @return
     */
    private Team getTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        return team;
    }
}




