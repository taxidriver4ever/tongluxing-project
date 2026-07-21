package com.tongluxing.admin.sos;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.support.CurrentUserContext;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class SosEventService {
    private final SosEventMapper mapper; private final CurrentUserContext currentUserContext;
    @Transactional public SosEventResponse create(CreateSosEventRequest r) {
        Long uid=currentUserContext.requireUserId(); SosEvent old=mapper.findByRequest(uid,r.requestId());
        if(old!=null)return response(old); LocalDateTime now=LocalDateTime.now(); SosEvent e=new SosEvent();
        e.setId(SnowflakeIdGenerator.nextId());e.setUserId(uid);e.setRequestId(r.requestId().trim());
        e.setLatitude(r.latitude());e.setLongitude(r.longitude());e.setLocationAccuracyMeters(r.locationAccuracyMeters());
        e.setAddress(r.address().trim());e.setMessage(r.message()==null?null:r.message().trim());
        e.setAlarmMode("MOCK");e.setEventStatus("PENDING");e.setOccurredAt(now);e.setCreatedAt(now);e.setUpdatedAt(now);
        mapper.insert(e);return response(e);
    }
    public List<SosEventResponse> list(String status,Integer limit){
        String s=status==null?"":status.trim().toUpperCase();
        if(!s.isEmpty()&&!List.of("PENDING","PROCESSING","RESOLVED").contains(s))
            throw new BusinessException(ResultCode.BAD_REQUEST,"SOS状态筛选值不正确");
        return mapper.list(s,limit==null?100:Math.max(1,Math.min(limit,200))).stream().map(this::response).toList();
    }
    @Transactional public SosEventResponse accept(Long id){Long op=currentUserContext.requireUserId();SosEvent e=require(id);
        if("PENDING".equals(e.getEventStatus()))mapper.accept(id,op,LocalDateTime.now());return response(require(id));}
    @Transactional public SosEventResponse resolve(Long id,ResolveSosEventRequest r){Long op=currentUserContext.requireUserId();SosEvent e=require(id);
        if("RESOLVED".equals(e.getEventStatus()))return response(e);
        if(mapper.resolve(id,op,r.resolutionNote().trim(),LocalDateTime.now())==0)
            throw new BusinessException(ResultCode.BAD_REQUEST,"当前SOS状态无法结案");return response(require(id));}
    private SosEvent require(Long id){SosEvent e=mapper.findById(id);if(e==null)throw new BusinessException(ResultCode.NOT_FOUND,"SOS事件不存在");return e;}
    private SosEventResponse response(SosEvent e){return new SosEventResponse(String.valueOf(e.getId()),String.valueOf(e.getUserId()),
        e.getRequestId(),e.getLatitude(),e.getLongitude(),e.getLocationAccuracyMeters(),e.getAddress(),e.getMessage(),
        e.getAlarmMode(),e.getEventStatus(),e.getAcceptedBy()==null?null:String.valueOf(e.getAcceptedBy()),e.getAcceptedAt(),
        e.getResolvedBy()==null?null:String.valueOf(e.getResolvedBy()),e.getResolvedAt(),e.getResolutionNote(),e.getOccurredAt());}
}
