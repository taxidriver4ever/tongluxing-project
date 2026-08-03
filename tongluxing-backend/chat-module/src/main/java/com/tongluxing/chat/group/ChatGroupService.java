package com.tongluxing.chat.group;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.chat.entity.ChatConversationMember;
import com.tongluxing.chat.mapper.ChatConversationMemberMapper;
import com.tongluxing.chat.mapper.ChatConversationMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.chat.service.TencentImService;
import com.tongluxing.chat.entity.ChatConversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
/**
 * 群聊协作业务服务，对上层提供稳定的领域操作入口。
 * 调用方无需了解底层表结构、状态校验和事务实现细节。
 */
@Service @RequiredArgsConstructor @Slf4j
public class ChatGroupService {
 private final ChatGroupMapper mapper; private final ChatConversationMemberMapper members;
 private final ChatConversationMapper conversations; private final CurrentUserContext current; private final ObjectMapper json;
 private final TripService trips;
 private final TeamService teams; private final TencentImService tencentIm;
 /** 加载群聊工作区及其关联的行程、成员和协作事项。 */
 public Map<String,Object> workspace(Long cid){ChatConversationMember me=requireMember(cid);Map<String,Object>w=requireWorkspace(cid);
  // 工作区主查询负责行程和群摘要，协作事项、位置、车辆按独立集合附加，保持响应结构清晰。
  w.put("selfRole",me.getMemberRole());w.put("items",mapper.items(cid));w.put("locations",mapper.locations(cid));
  w.put("memberVehicles",mapper.memberVehicles(cid));return w;}
 /** 校验请求并创建对应资源。 */
 @Transactional public Map<String,Object> createItem(Long cid,ChatGroupItemRequest r){ChatConversationMember me=requireMember(cid);
  // 公告、投票和提醒会影响全群成员，必须由 OWNER/ADMIN 创建；普通协作项允许成员创建。
  String type=r.itemType().toUpperCase();if(List.of("ANNOUNCEMENT","POLL","REMINDER").contains(type))requireManager(me);
  if("TRIP_CONFIRM".equals(type))throw new BusinessException(ResultCode.BAD_REQUEST,"请使用行程确认专用接口");
  Map<String,Object> payload="POLL".equals(type)?normalizePollPayload(r.payload()):r.payload();
  // 先写协作事项事实，再为需要在消息流展示的类型生成卡片消息；事务保证两者一致。
  Long id=SnowflakeIdGenerator.nextId();mapper.insertItem(id,cid,type,r.title().trim(),trim(r.content()),write(payload),current.requireUserId(),LocalDateTime.now());
  if(List.of("POLL","REMINDER").contains(type))persistCard(cid,current.requireUserId(),type+"_CARD",r.title(),r.content(),Map.of("itemId",String.valueOf(id)));
  return mapper.item(id);}
 /** 校验资源状态后更新对应数据。 */
 @Transactional public Map<String,Object> updateItem(Long cid,Long id,ChatGroupItemRequest r){ChatConversationMember me=requireMember(cid);
  // 同时校验资源存在及 conversationId 归属，防止用其他群的 itemId 越权修改。
  Map<String,Object>old=mapper.item(id);if(old==null||!cid.equals(longValue(old.get("conversationId"))))notFound();
  if(List.of("ANNOUNCEMENT","POLL","REMINDER").contains(String.valueOf(old.get("itemType"))))requireManager(me);
  else if(!current.requireUserId().equals(longValue(old.get("creatorUserId"))))requireOwner(me);
  Map<String,Object> payload="POLL".equals(old.get("itemType"))?normalizePollPayload(r.payload()):r.payload();
  mapper.updateItem(id,r.title().trim(),trim(r.content()),write(payload),LocalDateTime.now());return mapper.item(id);}
 /** 提交群聊投票；同一用户的重复投票由唯一约束控制。 */
 @Transactional public List<Map<String,Object>> vote(Long cid,Long itemId,ChatGroupVoteRequest r){requireMember(cid);
  Map<String,Object>item=mapper.item(itemId);if(item==null||!cid.equals(longValue(item.get("conversationId")))||!"POLL".equals(item.get("itemType")))notFound();
  if(!"ACTIVE".equals(item.get("itemStatus")))throw new BusinessException(ResultCode.BAD_REQUEST,"投票已结束");
  String option=r.optionKey().trim();if(!pollOptionKeys(item).contains(option))throw new BusinessException(ResultCode.BAD_REQUEST,"投票选项不存在");
  // 数据库唯一约束是并发下的最终保障；返回 0 被转换成用户可理解的重复投票提示。
  if(mapper.vote(SnowflakeIdGenerator.nextId(),itemId,option,current.requireUserId(),LocalDateTime.now())==0)
   throw new BusinessException(ResultCode.BAD_REQUEST,"你已经投过票，每人只能投一次");return mapper.votes(itemId);}
 /** 查询协作事项详情，并校验其所属会话。 */
 public Map<String,Object> itemDetails(Long cid,Long itemId){requireMember(cid);Map<String,Object> item=mapper.item(itemId);
  if(item==null||!cid.equals(longValue(item.get("conversationId"))))notFound();Map<String,Object> result=new LinkedHashMap<>(item);
  if("POLL".equals(item.get("itemType"))){result.put("votes",mapper.votes(itemId));result.put("myVote",mapper.userVote(itemId,current.requireUserId()));}return result;}
 /** 关闭进行中的投票，关闭后不再接受新选票。 */
 @Transactional public Map<String,Object> closePoll(Long cid,Long itemId){ChatConversationMember me=requireMember(cid);requireManager(me);
  Map<String,Object> item=mapper.item(itemId);if(item==null||!cid.equals(longValue(item.get("conversationId"))))notFound();
  mapper.closePoll(itemId,LocalDateTime.now());return itemDetails(cid,itemId);}
 /** 上报成员位置共享状态与坐标，并返回会话内可见位置。 */
 @Transactional public List<Map<String,Object>> location(Long cid,ChatLocationRequest r){requireMember(cid);Map<String,Object>w=requireWorkspace(cid);
  // 实时位置仅在行程进行中开放，避免发布阶段提前暴露成员位置。
  if(!List.of("RUNNING","ONGOING").contains(String.valueOf(w.get("tripStatus"))))throw new BusinessException(ResultCode.BAD_REQUEST,"行程开始后才能共享实时位置");
  mapper.location(SnowflakeIdGenerator.nextId(),cid,current.requireUserId(),r.latitude(),r.longitude(),r.speed(),r.sharing(),LocalDateTime.now());return mapper.locations(cid);}
 /** 创建举报记录，供管理端后续风控审核。 */
 @Transactional public Map<String,Object> report(Long cid,ChatReportRequest r){requireMember(cid);Long id=SnowflakeIdGenerator.nextId();
  mapper.report(id,cid,current.requireUserId(),r.targetType(),r.targetId(),r.reportType().trim(),r.reason().trim(),write(r.evidence()),LocalDateTime.now());
  return Map.of("reportId",String.valueOf(id),"status","PENDING");}
 /** 修改会话名称，并校验操作者的管理权限。 */
 @Transactional public Map<String,Object> rename(Long cid,String name){ChatConversationMember me=requireMember(cid);requireOwner(me);
  if(name==null||name.isBlank()||name.length()>64)throw new BusinessException(ResultCode.BAD_REQUEST,"群名称长度不正确");
  mapper.rename(cid,name.trim(),LocalDateTime.now());return workspace(cid);}
 /** 移除指定成员；调用方必须具有会话管理权限。 */
 @Transactional public void remove(Long cid,Long uid){ChatConversationMember me=requireMember(cid);requireOwner(me);
  if(current.requireUserId().equals(uid))throw new BusinessException(ResultCode.BAD_REQUEST,"队长不能移除自己");
  members.exit(cid,uid,LocalDateTime.now());}
 /** 更新成员角色，并确保角色值和操作者权限合法。 */
 @Transactional public void updateRole(Long cid,Long uid,String role){ChatConversationMember me=requireMember(cid);requireOwner(me);
  String normalized=role==null?"":role.trim().toUpperCase();if(!List.of("ADMIN","MEMBER","NAVIGATOR").contains(normalized))
   throw new BusinessException(ResultCode.BAD_REQUEST,"群成员角色不正确");
  if(mapper.updateMemberRole(cid,uid,normalized,LocalDateTime.now())==0)throw new BusinessException(ResultCode.BAD_REQUEST,"不能修改队长或成员不存在");}
 /** 校验请求并创建对应资源。 */
 @Transactional public Map<String,Object> createTripConfirmation(Long cid){ChatConversationMember me=requireMember(cid);requireOwner(me);
  Map<String,Object>w=requireWorkspace(cid);Long tripId=longValue(w.get("tripId"));if(tripId==null)throw new BusinessException(ResultCode.BAD_REQUEST,"当前群未绑定行程");
  if(!List.of("PUBLISHED","READY").contains(String.valueOf(w.get("tripStatus"))))throw new BusinessException(ResultCode.BAD_REQUEST,"只有待开始行程可发起确认");
  Map<String,Object> open=mapper.openConfirmation(cid);if(open!=null)return confirmationDetails(cid,longValue(open.get("id")));
  // 同一会话已有 OPEN 确认单时直接复用，阻止重复点击创建多套确认记录。
  LocalDateTime now=LocalDateTime.now();Long id=SnowflakeIdGenerator.nextId();Long owner=current.requireUserId();
  // 创建确认单快照时把当前有效群成员逐一写入；群主默认确认，其余成员等待表态。
  mapper.insertConfirmation(id,tripId,cid,owner,now);for(ChatConversationMember member:members.findActiveByConversationId(cid)){
   boolean isOwner="OWNER".equals(member.getMemberRole());mapper.insertConfirmationRecord(SnowflakeIdGenerator.nextId(),id,tripId,member.getUserId(),isOwner?"CONFIRMED":"WAITING",isOwner?now:null,now);}
  Map<String,Object>payload=new LinkedHashMap<>();payload.put("confirmationId",String.valueOf(id));payload.put("tripId",String.valueOf(tripId));
  payload.put("startName",w.get("startName"));payload.put("endName",w.get("endName"));payload.put("departureTime",w.get("departureTime"));
  mapper.insertItem(id,cid,"TRIP_CONFIRM",String.valueOf(w.get("tripName")),"请确认是否参加本次行程",write(payload),owner,now);
  // 确认单与聊天卡片共享业务 ID，客户端可从消息卡片准确进入对应确认详情。
  persistCard(cid,owner,"TRIP_CONFIRM_CARD",String.valueOf(w.get("tripName")),"请确认是否参加本次行程",payload);return confirmationDetails(cid,id);}
 /** 查询确认单详情并校验其所属会话。 */
 public Map<String,Object> confirmationDetails(Long cid,Long id){requireMember(cid);Map<String,Object> row=mapper.confirmation(cid,id);if(row==null)notFound();
  // 统计值由记录明细实时计算，避免维护冗余计数字段时发生不一致。
  Map<String,Object>result=new LinkedHashMap<>(row);List<Map<String,Object>>records=mapper.confirmationRecords(cid,id);result.put("records",records);
  result.put("confirmed",records.stream().filter(r->"CONFIRMED".equals(r.get("status"))).count());
  result.put("waiting",records.stream().filter(r->"WAITING".equals(r.get("status"))).count());
  result.put("rejected",records.stream().filter(r->"REJECTED".equals(r.get("status"))).count());return result;}
 /** 记录成员确认结果，并重新计算整体确认状态。 */
 @Transactional public Map<String,Object> respondConfirmation(Long cid,Long id,TripConfirmationRespondRequest request){requireMember(cid);
  Map<String,Object>row=mapper.confirmation(cid,id);if(row==null||!"OPEN".equals(row.get("confirmationStatus")))throw new BusinessException(ResultCode.BAD_REQUEST,"行程确认已结束");
  String status=request.status().toUpperCase();Long uid=current.requireUserId();if(mapper.respondConfirmation(id,uid,status,trim(request.reason()),LocalDateTime.now())==0)
   // 更新条件限制为 WAITING，因此同一成员只能首次提交，重复点击不会覆盖原决定。
   throw new BusinessException(ResultCode.BAD_REQUEST,"确认结果已提交，不能重复修改，或你不在本次确认名单中");
  persistCard(cid,null,"SYSTEM",status.equals("CONFIRMED")?"成员已确认参加":"成员暂不参加",null,Map.of("userId",String.valueOf(uid),"confirmationId",String.valueOf(id)));return confirmationDetails(cid,id);}
 /** 启动已完成成员确认的行程。 */
 @Transactional public Map<String,Object> startConfirmedTrip(Long cid,Long id){ChatConversationMember me=requireMember(cid);requireOwner(me);Map<String,Object>details=confirmationDetails(cid,id);
  if(!"OPEN".equals(details.get("confirmationStatus")))throw new BusinessException(ResultCode.BAD_REQUEST,"本次行程确认已结束");
  List<Map<String,Object>> records=(List<Map<String,Object>>)details.get("records");
  boolean ownerConfirmed=records.stream().anyMatch(r->"OWNER".equals(r.get("memberRole"))&&"CONFIRMED".equals(r.get("status")));
  // 群主必须确认且至少存在一名确认成员，防止空成员行程被错误启动。
  if(!ownerConfirmed)throw new BusinessException(ResultCode.BAD_REQUEST,"队长尚未确认，不能开启行程");
  if(records.stream().noneMatch(r->"CONFIRMED".equals(r.get("status"))))throw new BusinessException(ResultCode.BAD_REQUEST,"至少需要一名有效成员确认参加");Long tripId=longValue(details.get("tripId"));
  List<Long> confirmedUserIds=records.stream().filter(r->"CONFIRMED".equals(r.get("status"))).map(r->longValue(r.get("userId"))).filter(Objects::nonNull).toList();
  // 仅把已确认成员传给行程服务，未确认/拒绝成员不会进入实际出发行程名单。
  trips.startTrip(tripId,confirmedUserIds);mapper.closeConfirmation(id,LocalDateTime.now());persistCard(cid,null,"SYSTEM","行程正式开始",null,Map.of("tripId",String.valueOf(tripId),"confirmed",details.get("confirmed")));return workspace(cid);}
 /** 关闭当前资源，并阻止后续需要活跃状态的操作。 */
 @Transactional public void close(Long cid){ChatConversationMember me=requireMember(cid);requireOwner(me);
  Map<String,Object> workspace=requireWorkspace(cid);Long tripId=longValue(workspace.get("tripId"));
  if(tripId==null)throw new BusinessException(ResultCode.BAD_REQUEST,"当前群未绑定行程");
  ChatConversation conversation=conversations.findById(cid);
  // 解散是跨行程、车队和聊天的领域操作：先取消关联业务，再归档会话和退出成员。
  trips.cancelTrip(tripId);teams.dissolveTrip(tripId);
  persistCard(cid,current.requireUserId(),"SYSTEM","群主已解散群聊","关联行程已取消，所有成员已退出",Map.of("tripId",String.valueOf(tripId)));
  LocalDateTime now=LocalDateTime.now();conversations.archive(cid,now);members.exitAll(cid,now);
  if(conversation!=null&&"TENCENT_IM".equals(conversation.getProviderType())&&tencentIm.isConfigured()
    &&conversation.getProviderConversationKey()!=null&&!conversation.getProviderConversationKey().isBlank()){
   // 云群销毁失败不能回滚已经完成的本地解散，否则会恢复已取消行程；仅记录告警供补偿。
   try{tencentIm.destroyGroup(conversation.getProviderConversationKey());}
   catch(RuntimeException ex){log.warn("销毁腾讯 IM 群失败，conversationId={}",cid,ex);}
  }}
 /** 查询待处理或已处理的聊天举报记录。 */
 public List<Map<String,Object>> reports(String status,Integer limit){return mapper.reports(norm(status),safe(limit));}
 /** 查询消息风控命中记录。 */
 public List<Map<String,Object>> risks(String status,Integer limit){return mapper.risks(norm(status),safe(limit));}
 /** 查询行程确认单及其处理状态。 */
 public List<Map<String,Object>> confirmations(String status,Integer limit){return mapper.confirmations(norm(status),safe(limit));}
 /** 审核待处理记录，并持久化审核结论。 */
 @Transactional public Map<String,Object> review(Long id,ChatRiskReviewRequest r){Long op=current.requireUserId();
  if(mapper.review(id,r.decision(),op,r.note().trim(),LocalDateTime.now())==0)throw new BusinessException(ResultCode.BAD_REQUEST,"举报已处理或不存在");
  return Map.of("reportId",String.valueOf(id),"status",r.decision(),"reviewerId",String.valueOf(op));}
 private ChatConversationMember requireMember(Long cid){Long uid=current.requireUserId();if(conversations.findById(cid)==null)notFound();
  // 所有工作区操作统一经过此入口，避免各方法遗漏会话存在性或成员状态校验。
  ChatConversationMember m=members.findByConversationAndUser(cid,uid);if(m==null||!"ACTIVE".equals(m.getMemberStatus()))throw new BusinessException(ResultCode.FORBIDDEN,"你不是当前群成员");return m;}
 private void requireOwner(ChatConversationMember m){if(!"OWNER".equals(m.getMemberRole()))throw new BusinessException(ResultCode.FORBIDDEN,"只有队长可以执行此操作");}
 private void requireManager(ChatConversationMember m){if(!List.of("OWNER","ADMIN").contains(m.getMemberRole()))throw new BusinessException(ResultCode.FORBIDDEN,"只有队长或管理员可以执行此操作");}
 private void persistCard(Long cid,Long sender,String type,String title,String content,Map<String,Object>extra){LocalDateTime now=LocalDateTime.now();Long mid=SnowflakeIdGenerator.nextId();
  // 卡片使用普通消息表承载，payload 保存跳转所需业务字段，客户端无需另建消息通道。
  Map<String,Object>payload=new LinkedHashMap<>();payload.put("content",content==null?title:content);payload.put("title",title);if(extra!=null)payload.putAll(extra);
  mapper.insertCardMessage(mid,cid,sender,type,write(payload),"business-card-"+mid,now);mapper.updateReminderPreview(cid,mid,title,now);
  // 系统卡片没有发送者，给所有成员增加未读；成员卡片则排除发送者本人。
  if(sender==null)mapper.incrementReminderUnread(cid,now);else members.incrementUnread(cid,sender,now);}
 private Map<String,Object> requireWorkspace(Long id){Map<String,Object>w=mapper.workspace(id);if(w==null)notFound();return new LinkedHashMap<>(w);}
 private void notFound(){throw new BusinessException(ResultCode.NOT_FOUND,"群聊数据不存在");}
 private String write(Object v){try{return v==null?"{}":json.writeValueAsString(v);}catch(Exception e){throw new BusinessException(ResultCode.BAD_REQUEST,"业务数据格式错误");}}
 private String trim(String s){return s==null?null:s.trim();} private String norm(String s){return s==null?"":s.trim().toUpperCase();}
 private int safe(Integer n){return n==null?100:Math.max(1,Math.min(n,200));}
 private Long longValue(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}
 @SuppressWarnings("unchecked") private Map<String,Object> normalizePollPayload(Map<String,Object> raw){
  // 服务端统一校验并标准化选项，不能依赖客户端生成合法 key 或过滤重复文案。
  Object source=raw==null?null:raw.get("options");if(!(source instanceof List<?> values)||values.size()<2||values.size()>8)
   throw new BusinessException(ResultCode.BAD_REQUEST,"投票需要设置2至8个选项");
  List<Map<String,Object>> options=new ArrayList<>();Set<String> labels=new HashSet<>();int index=0;
  for(Object value:values){String label;String key;
   if(value instanceof Map<?,?> map){Object labelValue=map.get("label"),keyValue=map.get("key");label=labelValue==null?"":String.valueOf(labelValue).trim();key=keyValue==null?"":String.valueOf(keyValue).trim();}
   else{label=String.valueOf(value).trim();key="";}
   if(label.isBlank()||label.length()>80||!labels.add(label))throw new BusinessException(ResultCode.BAD_REQUEST,"投票选项不能为空、重复或超过80字");
   if(key.isBlank())key=String.valueOf((char)('A'+index));options.add(Map.of("key",key,"label",label));index++;}
  // 保留 payload 中除 options 外的扩展字段，只用标准化后的选项覆盖原始输入。
  Map<String,Object> result=new LinkedHashMap<>();if(raw!=null)result.putAll(raw);result.put("options",options);return result;}
 @SuppressWarnings("unchecked") private Set<String> pollOptionKeys(Map<String,Object> item){try{
  // 投票时从事项快照解析合法 key，避免客户端提交不属于该投票的选项。
  Map<String,Object> payload=json.readValue(String.valueOf(item.get("payloadJson")),Map.class);Object values=payload.get("options");Set<String> keys=new HashSet<>();
  if(values instanceof List<?> list)for(Object value:list)if(value instanceof Map<?,?> map)keys.add(String.valueOf(map.get("key")));return keys;
 }catch(Exception e){throw new BusinessException(ResultCode.BAD_REQUEST,"投票数据格式错误");}}
}
