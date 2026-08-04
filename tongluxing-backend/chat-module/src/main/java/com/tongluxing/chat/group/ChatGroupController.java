package com.tongluxing.chat.group;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 负责群聊协作相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/chats/conversations/{conversationId}/group")
public class ChatGroupController {
 private final ChatGroupService service;
 /** 加载群聊工作区及其关联的行程、成员和协作事项。 */
 @GetMapping public Result<Map<String,Object>> workspace(@PathVariable Long conversationId){return Result.success(service.workspace(conversationId));}
 /** 创建群聊协作事项并返回最新数据。 */
 @PostMapping("/items") public Result<Map<String,Object>> item(@PathVariable Long conversationId,@Valid @RequestBody ChatGroupItemRequest r){return Result.success(service.createItem(conversationId,r));}
 /** 校验当前状态后更新资源。 */
 @PutMapping("/items/{itemId}") public Result<Map<String,Object>> update(@PathVariable Long conversationId,@PathVariable Long itemId,@Valid @RequestBody ChatGroupItemRequest r){return Result.success(service.updateItem(conversationId,itemId,r));}
 /** 查询协作事项详情，并校验其所属会话。 */
 @GetMapping("/items/{itemId}") public Result<Map<String,Object>> itemDetails(@PathVariable Long conversationId,@PathVariable Long itemId){return Result.success(service.itemDetails(conversationId,itemId));}
 /** 提交群聊投票；同一用户的重复投票由唯一约束控制。 */
 @PostMapping("/polls/{itemId}/vote") public Result<List<Map<String,Object>>> vote(@PathVariable Long conversationId,@PathVariable Long itemId,@Valid @RequestBody ChatGroupVoteRequest r){return Result.success(service.vote(conversationId,itemId,r));}
 /** 关闭进行中的投票，关闭后不再接受新选票。 */
 @PostMapping("/polls/{itemId}/close") public Result<Map<String,Object>> closePoll(@PathVariable Long conversationId,@PathVariable Long itemId){return Result.success(service.closePoll(conversationId,itemId));}
 /** 上报成员位置共享状态与坐标，并返回会话内可见位置。 */
 @PostMapping("/location") public Result<List<Map<String,Object>>> location(@PathVariable Long conversationId,@Valid @RequestBody ChatLocationRequest r){return Result.success(service.location(conversationId,r));}
 /** 创建举报记录，供管理端后续风控审核。 */
 @PostMapping("/reports") public Result<Map<String,Object>> report(@PathVariable Long conversationId,@Valid @RequestBody ChatReportRequest r){return Result.success(service.report(conversationId,r));}
 /** 修改会话名称，并校验操作者的管理权限。 */
 @PutMapping("/name") public Result<Map<String,Object>> rename(@PathVariable Long conversationId,@RequestBody Map<String,String> r){return Result.success(service.rename(conversationId,r.get("name")));}
 /** 移除指定成员；调用方必须具有会话管理权限。 */
 @DeleteMapping("/members/{userId}") public Result<Void> remove(@PathVariable Long conversationId,@PathVariable Long userId){service.remove(conversationId,userId);return Result.success();}
 /** 更新指定成员的群聊角色。 */
 @PutMapping("/members/{userId}/role") public Result<Void> role(@PathVariable Long conversationId,@PathVariable Long userId,@Valid @RequestBody ChatMemberRoleRequest r){service.updateRole(conversationId,userId,r.role());return Result.success();}
 /** 创建行程出发确认单，供会话成员逐一确认。 */
 @PostMapping("/trip-confirmations") public Result<Map<String,Object>> createConfirmation(@PathVariable Long conversationId){return Result.success(service.createTripConfirmation(conversationId));}
 /** 查询行程确认单及成员响应明细。 */
 @GetMapping("/trip-confirmations/{id}") public Result<Map<String,Object>> confirmation(@PathVariable Long conversationId,@PathVariable Long id){return Result.success(service.confirmationDetails(conversationId,id));}
 /** 记录成员对行程确认单的响应，并返回最新确认状态。 */
 @PostMapping("/trip-confirmations/{id}/respond") public Result<Map<String,Object>> respond(@PathVariable Long conversationId,@PathVariable Long id,@Valid @RequestBody TripConfirmationRespondRequest r){return Result.success(service.respondConfirmation(conversationId,id,r));}
 /** 在满足业务前置条件后启动已确认的行程。 */
 @PostMapping("/trip-confirmations/{id}/start") public Result<Map<String,Object>> start(@PathVariable Long conversationId,@PathVariable Long id,@RequestHeader(value="X-Client-Type",required=false)String clientType){if("MINI_PROGRAM".equalsIgnoreCase(clientType)){throw new BusinessException("小程序暂不支持开启行程，请下载同路行 App 使用此功能");}return Result.success(service.startConfirmedTrip(conversationId,id));}
 /** 关闭当前资源，并阻止后续需要活跃状态的操作。 */
 @PostMapping({"/close","/dissolve"}) public Result<Void> close(@PathVariable Long conversationId){service.close(conversationId);return Result.success();}
}
