package com.tongdao.growth.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.growth.integration.GrowthFacade.GrowthGrantResult;
import com.tongdao.growth.mapper.GrowthMapper;
import com.tongdao.growth.service.GrowthService;
import com.tongdao.user.model.UserModels.*;
import com.tongdao.user.support.CurrentUserContext;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class GrowthServiceImpl implements GrowthService {
    private final GrowthMapper mapper;
    private final CurrentUserContext currentUser;

    @Override public GrowthSummaryVO getCurrentSummary(){return getSummary(currentUser.requireUserId());}
    @Override public BadgeWallVO getCurrentBadges(){return getBadgeWall(currentUser.requireUserId());}
    @Override public PageResult<GrowthLogVO> getCurrentLogs(int page,int size){
        long userId=currentUser.requireUserId(); int p=Math.max(1,page),s=Math.min(100,Math.max(1,size));
        List<GrowthLogVO> rows=mapper.findLogs(userId,(p-1)*s,s).stream().map(this::log).toList();
        return new PageResult<>(rows,mapper.countLogs(userId),p,s);
    }
    @Override public GrowthSummaryVO getSummary(Long userId){
        Map<String,Object> row=ensureAccount(userId); int points=num(row,"totalPoints");
        Integer next=mapper.findNextLevelPoints(points);
        return new GrowthSummaryVO(points,str(row,"levelCode"),next==null?0:Math.max(0,next-points));
    }
    @Override public BadgeWallVO getBadgeWall(Long userId){
        return new BadgeWallVO(mapper.findEarnedBadges(userId).stream().map(this::badge).toList(),mapper.findLockedBadges(userId).stream().map(this::badge).toList());
    }
    @Override @Transactional public GrowthGrantResult grant(Long userId,String bizType,String bizId,int points,String remark){
        LocalDateTime now=LocalDateTime.now(); Map<String,Object> account=ensureAccountForUpdate(userId);
        int old=num(account,"totalPoints"),balance=old+points;
        if(balance<0) throw new BusinessException(ResultCode.BAD_REQUEST,"成长值余额不足");
        try{mapper.insertLog(SnowflakeIdGenerator.nextId(),userId,bizType,bizId,points,balance,remark,now);}catch(DuplicateKeyException e){
            return new GrowthGrantResult(userId,points,old,str(account,"levelCode"),true);
        }
        String level=mapper.findLevelCode(balance); if(level==null) level="LV1";
        if(mapper.updateAccount(lng(account,"id"),balance,level,num(account,"version"),now)==0) throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,"成长账户并发更新失败");
        int count=mapper.countEvents(userId,bizType); for(Long badgeId:mapper.findEligibleBadges(bizType,count)) mapper.insertUserBadge(SnowflakeIdGenerator.nextId(),userId,badgeId,bizId,now);
        return new GrowthGrantResult(userId,points,balance,level,false);
    }
    private Map<String,Object> ensureAccount(Long userId){Map<String,Object> r=mapper.findAccount(userId);if(r!=null)return r;try{mapper.insertAccount(SnowflakeIdGenerator.nextId(),userId,LocalDateTime.now());}catch(DuplicateKeyException ignored){}return mapper.findAccount(userId);}
    private Map<String,Object> ensureAccountForUpdate(Long userId){Map<String,Object> r=mapper.findAccountForUpdate(userId);if(r!=null)return r;try{mapper.insertAccount(SnowflakeIdGenerator.nextId(),userId,LocalDateTime.now());}catch(DuplicateKeyException ignored){}return mapper.findAccountForUpdate(userId);}
    private GrowthLogVO log(Map<String,Object> r){return new GrowthLogVO(lng(r,"id"),str(r,"bizType"),str(r,"bizId"),num(r,"pointDelta"),num(r,"balanceAfter"),str(r,"remark"),time(r,"createdAt"));}
    private BadgeVO badge(Map<String,Object> r){return new BadgeVO(lng(r,"badgeId"),str(r,"badgeCode"),str(r,"badgeName"),str(r,"badgeImageKey"),time(r,"awardedAt"));}
    private String str(Map<String,Object> r,String k){Object v=r.get(k);return v==null?"":v.toString();} private int num(Map<String,Object> r,String k){Object v=r.get(k);return v instanceof Number n?n.intValue():Integer.parseInt(v.toString());} private long lng(Map<String,Object> r,String k){Object v=r.get(k);return v instanceof Number n?n.longValue():Long.parseLong(v.toString());} private LocalDateTime time(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v instanceof LocalDateTime t?t:LocalDateTime.parse(v.toString().replace(' ','T'));}
}
