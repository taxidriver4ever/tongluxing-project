package com.tongluxing.chat.group;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;
/**
 * 群聊协作 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface ChatGroupMapper {
 @Select("""
 select c.id conversationId,c.conversation_name conversationName,c.biz_id tripId,c.conversation_status conversationStatus,
   t.title tripName,t.trip_number tripNumber,t.start_name startName,t.end_name endName,t.departure_time departureTime,t.status tripStatus,
   t.joined_vehicle_count vehicleCount,(select count(*) from chat_conversation_member m where m.conversation_id=c.id and m.member_status='ACTIVE' and m.deleted=0) memberCount
   from chat_conversation c left join trip t on c.biz_type='TRIP' and t.id=c.biz_id and t.deleted=0 where c.id=#{id} and c.deleted=0""")
 Map<String,Object> workspace(@Param("id")Long id);
 @Select("""
 select i.id,i.conversation_id conversationId,i.item_type itemType,i.title,i.content,i.payload_json payloadJson,
   i.item_status itemStatus,i.creator_user_id creatorUserId,i.created_at createdAt,i.updated_at updatedAt,
   (select count(*) from chat_group_vote v where v.item_id=i.id) voteCount
   from chat_group_item i where i.conversation_id=#{id} and i.item_status in ('ACTIVE','CLOSED') and i.deleted=0 order by i.created_at desc""")
 List<Map<String,Object>> items(@Param("id")Long id);
 @Insert("""
 insert into chat_group_item(id,conversation_id,item_type,title,content,payload_json,item_status,creator_user_id,created_at,updated_at,deleted)
   values(#{id},#{conversationId},#{type},#{title},#{content},#{payload},'ACTIVE',#{creator},#{now},#{now},0)""")
 void insertItem(@Param("id")Long id,@Param("conversationId")Long conversationId,@Param("type")String type,
   @Param("title")String title,@Param("content")String content,@Param("payload")String payload,@Param("creator")Long creator,@Param("now")LocalDateTime now);
 @Select("""
 select id,conversation_id conversationId,item_type itemType,title,content,payload_json payloadJson,
   item_status itemStatus,creator_user_id creatorUserId,created_at createdAt,updated_at updatedAt
   from chat_group_item where id=#{id} and deleted=0 limit 1""") Map<String,Object> item(@Param("id")Long id);
 @Update("""
 update chat_group_item set title=#{title},content=#{content},payload_json=#{payload},updated_at=#{now}
   where id=#{id} and item_status='ACTIVE' and deleted=0""")
 int updateItem(@Param("id")Long id,@Param("title")String title,@Param("content")String content,@Param("payload")String payload,@Param("now")LocalDateTime now);
 @Update("update chat_group_item set item_status='CLOSED',updated_at=#{now} where id=#{id} and item_type='POLL' and item_status='ACTIVE' and deleted=0")
 int closePoll(@Param("id")Long id,@Param("now")LocalDateTime now);
 @Insert("""
 insert ignore into chat_group_vote(id,item_id,option_key,user_id,created_at)
 values(#{id},#{itemId},#{optionKey},#{userId},#{now})""")
 int vote(@Param("id")Long id,@Param("itemId")Long itemId,@Param("optionKey")String optionKey,@Param("userId")Long userId,@Param("now")LocalDateTime now);
 @Select("select option_key optionKey,count(*) votes from chat_group_vote where item_id=#{id} group by option_key") List<Map<String,Object>> votes(@Param("id")Long id);
 @Select("select option_key from chat_group_vote where item_id=#{itemId} and user_id=#{userId} limit 1")
 String userVote(@Param("itemId")Long itemId,@Param("userId")Long userId);
 @Insert("""
 insert into chat_member_location(id,conversation_id,user_id,latitude,longitude,speed,sharing_flag,recorded_at,updated_at)
   values(#{id},#{conversationId},#{userId},#{lat},#{lng},#{speed},#{sharing},#{now},#{now})
   on duplicate key update latitude=values(latitude),longitude=values(longitude),speed=values(speed),sharing_flag=values(sharing_flag),recorded_at=values(recorded_at),updated_at=values(updated_at)""")
 void location(@Param("id")Long id,@Param("conversationId")Long conversationId,@Param("userId")Long userId,
   @Param("lat")BigDecimal lat,@Param("lng")BigDecimal lng,@Param("speed")BigDecimal speed,@Param("sharing")boolean sharing,@Param("now")LocalDateTime now);
 @Select("""
 select l.user_id userId,p.nickname,p.avatar_image_key avatarImageKey,l.latitude,l.longitude,l.speed,l.recorded_at recordedAt
   from chat_member_location l left join user_profile p on p.user_id=l.user_id and p.deleted=0
   where l.conversation_id=#{id} and l.sharing_flag=1 and l.recorded_at>=date_sub(now(),interval 15 minute) order by l.recorded_at desc""")
 List<Map<String,Object>> locations(@Param("id")Long id);
 @Select("""
 select m.user_id userId,v.brand,v.model,v.color,v.plate_no_mask plateNoMask,v.vehicle_photo_image_key vehiclePhotoImageKey
   from chat_conversation_member m join vehicle_profile v on v.user_id=m.user_id and v.is_default=1
     and v.certification_status='APPROVED' and v.deleted=0
   where m.conversation_id=#{id} and m.member_status='ACTIVE' and m.deleted=0
 """)
 List<Map<String,Object>> memberVehicles(@Param("id")Long id);
 @Select("""
 select i.id,i.conversation_id conversationId,i.title,i.content,i.payload_json payloadJson,
   c.provider_type providerType,c.provider_conversation_key providerConversationKey,
   (select m.user_id from chat_conversation_member m where m.conversation_id=i.conversation_id
     and m.member_role='OWNER' and m.member_status='ACTIVE' and m.deleted=0 limit 1) ownerUserId
   from chat_group_item i join chat_conversation c on c.id=i.conversation_id and c.conversation_status='ACTIVE' and c.deleted=0
   left join chat_reminder_dispatch d on d.item_id=i.id
   where i.item_type='REMINDER' and i.item_status='ACTIVE' and i.deleted=0 and d.item_id is null
     and json_unquote(json_extract(i.payload_json,'$.scheduledAt')) is not null
     and str_to_date(json_unquote(json_extract(i.payload_json,'$.scheduledAt')),'%Y-%m-%dT%H:%i:%s')<=now()
   order by i.created_at asc limit 50
 """) List<Map<String,Object>> dueReminders();
 @Insert("insert ignore into chat_reminder_dispatch(item_id,dispatched_at) values(#{itemId},#{now})")
 int markReminderDispatched(@Param("itemId")Long itemId,@Param("now")LocalDateTime now);
 @Insert("""
 insert into chat_message(id,conversation_id,sender_user_id,message_type,message_payload_json,message_status,
   provider_message_key,sent_at,created_at,updated_at,deleted)
 values(#{id},#{conversationId},null,'SYSTEM',#{payload},'NORMAL',#{providerKey},#{now},#{now},#{now},0)
 """) void insertReminderMessage(@Param("id")Long id,@Param("conversationId")Long conversationId,
   @Param("payload")String payload,@Param("providerKey")String providerKey,@Param("now")LocalDateTime now);
 @Insert("""
 insert into chat_message(id,conversation_id,sender_user_id,message_type,message_payload_json,message_status,
   provider_message_key,sent_at,created_at,updated_at,deleted)
 values(#{id},#{conversationId},#{senderUserId},#{messageType},#{payload},'NORMAL',#{providerKey},#{now},#{now},#{now},0)
 """) void insertCardMessage(@Param("id")Long id,@Param("conversationId")Long conversationId,
   @Param("senderUserId")Long senderUserId,@Param("messageType")String messageType,
   @Param("payload")String payload,@Param("providerKey")String providerKey,@Param("now")LocalDateTime now);
 @Update("""
 update chat_conversation set last_message_id=#{messageId},last_message_preview=#{preview},last_message_at=#{now},updated_at=#{now}
 where id=#{conversationId} and conversation_status='ACTIVE' and deleted=0
 """) void updateReminderPreview(@Param("conversationId")Long conversationId,@Param("messageId")Long messageId,
   @Param("preview")String preview,@Param("now")LocalDateTime now);
 @Update("""
 update chat_conversation_member set unread_count=unread_count+1,updated_at=#{now}
 where conversation_id=#{conversationId} and member_status='ACTIVE' and deleted=0
 """) void incrementReminderUnread(@Param("conversationId")Long conversationId,@Param("now")LocalDateTime now);
 @Update("""
 update chat_conversation_member set member_role=#{role},updated_at=#{now}
 where conversation_id=#{conversationId} and user_id=#{userId} and member_role<>'OWNER'
   and member_status='ACTIVE' and deleted=0
 """) int updateMemberRole(@Param("conversationId")Long conversationId,@Param("userId")Long userId,
   @Param("role")String role,@Param("now")LocalDateTime now);
 @Insert("""
 insert into trip_confirmation(id,trip_id,conversation_id,creator_user_id,confirmation_status,created_at)
 values(#{id},#{tripId},#{conversationId},#{creator},'OPEN',#{now})
 """) void insertConfirmation(@Param("id")Long id,@Param("tripId")Long tripId,@Param("conversationId")Long conversationId,
   @Param("creator")Long creator,@Param("now")LocalDateTime now);
 @Insert("""
 insert into trip_confirm_record(id,confirmation_id,trip_id,user_id,status,confirm_time,created_at,updated_at)
 values(#{id},#{confirmationId},#{tripId},#{userId},#{status},#{confirmTime},#{now},#{now})
 """) void insertConfirmationRecord(@Param("id")Long id,@Param("confirmationId")Long confirmationId,
   @Param("tripId")Long tripId,@Param("userId")Long userId,@Param("status")String status,
   @Param("confirmTime")LocalDateTime confirmTime,@Param("now")LocalDateTime now);
 @Select("""
 select id,trip_id tripId,conversation_id conversationId,creator_user_id creatorUserId,
   confirmation_status confirmationStatus,created_at createdAt,closed_at closedAt
 from trip_confirmation where id=#{id} and conversation_id=#{conversationId} limit 1
 """) Map<String,Object> confirmation(@Param("conversationId")Long conversationId,@Param("id")Long id);
 @Update("""
 update trip_confirm_record set status=#{status},reject_reason=#{reason},confirm_time=#{now},updated_at=#{now}
 where confirmation_id=#{confirmationId} and user_id=#{userId} and status='WAITING'
 """) int respondConfirmation(@Param("confirmationId")Long confirmationId,@Param("userId")Long userId,
   @Param("status")String status,@Param("reason")String reason,@Param("now")LocalDateTime now);
 @Select("""
 select r.user_id userId,p.nickname,p.avatar_image_key avatarImageKey,m.member_role memberRole,
   r.status,r.reject_reason rejectReason,r.confirm_time confirmTime
 from trip_confirm_record r
 left join user_profile p on p.user_id=r.user_id and p.deleted=0
 left join chat_conversation_member m on m.conversation_id=#{conversationId} and m.user_id=r.user_id and m.deleted=0
 where r.confirmation_id=#{confirmationId}
 order by case m.member_role when 'OWNER' then 0 when 'ADMIN' then 1 else 2 end,p.nickname
 """) List<Map<String,Object>> confirmationRecords(@Param("conversationId")Long conversationId,@Param("confirmationId")Long confirmationId);
 @Select("""
 select id,trip_id tripId,conversation_id conversationId,creator_user_id creatorUserId,
   confirmation_status confirmationStatus,created_at createdAt,closed_at closedAt
 from trip_confirmation where conversation_id=#{conversationId} and confirmation_status='OPEN'
 order by created_at desc limit 1
 """) Map<String,Object> openConfirmation(@Param("conversationId")Long conversationId);
 @Update("update trip_confirmation set confirmation_status='CLOSED',closed_at=#{now} where id=#{id} and confirmation_status='OPEN'")
 int closeConfirmation(@Param("id")Long id,@Param("now")LocalDateTime now);
 @Insert("""
 insert into chat_report(id,conversation_id,reporter_user_id,target_type,target_id,report_type,reason,evidence_json,
   report_status,created_at,updated_at) values(#{id},#{conversationId},#{reporter},#{targetType},#{targetId},#{reportType},#{reason},#{evidence},'PENDING',#{now},#{now})""")
 void report(@Param("id")Long id,@Param("conversationId")Long conversationId,@Param("reporter")Long reporter,
  @Param("targetType")String targetType,@Param("targetId")String targetId,@Param("reportType")String reportType,
  @Param("reason")String reason,@Param("evidence")String evidence,@Param("now")LocalDateTime now);
 @Select("""
 select id,conversation_id conversationId,reporter_user_id reporterUserId,target_type targetType,target_id targetId,
   report_type reportType,reason,evidence_json evidenceJson,report_status reportStatus,reviewer_id reviewerId,
   review_note reviewNote,reviewed_at reviewedAt,created_at createdAt,updated_at updatedAt
   from chat_report where (#{status}='' or report_status=#{status}) order by created_at desc limit #{limit}
 """)
 List<Map<String,Object>> reports(@Param("status")String status,@Param("limit")int limit);
 @Update("""
 update chat_report set report_status=#{decision},reviewer_id=#{reviewer},review_note=#{note},reviewed_at=#{now},updated_at=#{now}
   where id=#{id} and report_status='PENDING'""")
 int review(@Param("id")Long id,@Param("decision")String decision,@Param("reviewer")Long reviewer,@Param("note")String note,@Param("now")LocalDateTime now);
 @Select("""
 select r.id,r.message_id messageId,r.risk_level riskLevel,r.risk_type riskType,r.confidence,r.status,r.matched_rule matchedRule,
   m.conversation_id conversationId,m.sender_user_id senderUserId,m.message_payload_json messagePayloadJson,r.created_at createdAt
   from message_risk r join chat_message m on m.id=r.message_id where (#{status}='' or r.status=#{status}) order by r.created_at desc limit #{limit}""")
 List<Map<String,Object>> risks(@Param("status")String status,@Param("limit")int limit);
 @Select("""
 select tc.id,tc.trip_id tripId,tc.conversation_id conversationId,t.title tripName,
   tc.creator_user_id creatorUserId,tc.confirmation_status confirmationStatus,tc.created_at createdAt,tc.closed_at closedAt,
   count(r.id) memberCount,
   sum(case when r.status='CONFIRMED' then 1 else 0 end) confirmedCount,
   sum(case when r.status='WAITING' then 1 else 0 end) waitingCount,
   sum(case when r.status='REJECTED' then 1 else 0 end) rejectedCount
 from trip_confirmation tc
 left join trip_confirm_record r on r.confirmation_id=tc.id
 left join trip t on t.id=tc.trip_id and t.deleted=0
 where (#{status}='' or tc.confirmation_status=#{status})
 group by tc.id,tc.trip_id,tc.conversation_id,t.title,tc.creator_user_id,tc.confirmation_status,tc.created_at,tc.closed_at
 order by tc.created_at desc limit #{limit}
 """) List<Map<String,Object>> confirmations(@Param("status")String status,@Param("limit")int limit);
 @Update("update chat_conversation set conversation_name=#{name},updated_at=#{now} where id=#{id} and conversation_status='ACTIVE' and deleted=0")
 int rename(@Param("id")Long id,@Param("name")String name,@Param("now")LocalDateTime now);
}
