package com.tongluxing.admin.sos;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
/**
 * SOS 紧急事件 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface SosEventMapper {
    @Insert("""
      insert into sos_event(id,user_id,request_id,latitude,longitude,location_accuracy_meters,address,message,
      alarm_mode,event_status,occurred_at,created_at,updated_at,deleted)
      values(#{id},#{userId},#{requestId},#{latitude},#{longitude},#{locationAccuracyMeters},#{address},#{message},
      #{alarmMode},#{eventStatus},#{occurredAt},#{createdAt},#{updatedAt},0)
      """) void insert(SosEvent event);
    @Select("select * from sos_event where user_id=#{userId} and request_id=#{requestId} and deleted=0 limit 1")
    SosEvent findByRequest(@Param("userId") Long userId,@Param("requestId") String requestId);
    @Select("select * from sos_event where id=#{id} and deleted=0 limit 1") SosEvent findById(@Param("id") Long id);
    @Select("""
      select * from sos_event where deleted=0 and (#{status}='' or event_status=#{status})
      order by case event_status when 'PENDING' then 0 when 'PROCESSING' then 1 else 2 end,occurred_at desc limit #{limit}
      """)
    List<SosEvent> list(@Param("status") String status,@Param("limit") int limit);
    @Update("""
      update sos_event set event_status='PROCESSING',accepted_by=#{operatorId},accepted_at=#{now},updated_at=#{now}
      where id=#{id} and event_status='PENDING' and deleted=0
      """)
    int accept(@Param("id") Long id,@Param("operatorId") Long operatorId,@Param("now") LocalDateTime now);
    @Update("""
      update sos_event set event_status='RESOLVED',resolved_by=#{operatorId},resolved_at=#{now},
      resolution_note=#{note},updated_at=#{now} where id=#{id} and event_status in ('PENDING','PROCESSING') and deleted=0
      """)
    int resolve(@Param("id") Long id,@Param("operatorId") Long operatorId,@Param("note") String note,@Param("now") LocalDateTime now);
}
