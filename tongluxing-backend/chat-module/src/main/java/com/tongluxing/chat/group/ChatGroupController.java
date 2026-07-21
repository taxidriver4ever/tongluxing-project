package com.tongluxing.chat.group;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/chats/conversations/{conversationId}/group")
public class ChatGroupController {
 private final ChatGroupService service;
 @GetMapping public Result<Map<String,Object>> workspace(@PathVariable Long conversationId){return Result.success(service.workspace(conversationId));}
 @PostMapping("/items") public Result<Map<String,Object>> item(@PathVariable Long conversationId,@Valid @RequestBody ChatGroupItemRequest r){return Result.success(service.createItem(conversationId,r));}
 @PutMapping("/items/{itemId}") public Result<Map<String,Object>> update(@PathVariable Long conversationId,@PathVariable Long itemId,@Valid @RequestBody ChatGroupItemRequest r){return Result.success(service.updateItem(conversationId,itemId,r));}
 @GetMapping("/items/{itemId}") public Result<Map<String,Object>> itemDetails(@PathVariable Long conversationId,@PathVariable Long itemId){return Result.success(service.itemDetails(conversationId,itemId));}
 @PostMapping("/polls/{itemId}/vote") public Result<List<Map<String,Object>>> vote(@PathVariable Long conversationId,@PathVariable Long itemId,@Valid @RequestBody ChatGroupVoteRequest r){return Result.success(service.vote(conversationId,itemId,r));}
 @PostMapping("/polls/{itemId}/close") public Result<Map<String,Object>> closePoll(@PathVariable Long conversationId,@PathVariable Long itemId){return Result.success(service.closePoll(conversationId,itemId));}
 @PostMapping("/location") public Result<List<Map<String,Object>>> location(@PathVariable Long conversationId,@Valid @RequestBody ChatLocationRequest r){return Result.success(service.location(conversationId,r));}
 @PostMapping("/reports") public Result<Map<String,Object>> report(@PathVariable Long conversationId,@Valid @RequestBody ChatReportRequest r){return Result.success(service.report(conversationId,r));}
 @PutMapping("/name") public Result<Map<String,Object>> rename(@PathVariable Long conversationId,@RequestBody Map<String,String> r){return Result.success(service.rename(conversationId,r.get("name")));}
 @DeleteMapping("/members/{userId}") public Result<Void> remove(@PathVariable Long conversationId,@PathVariable Long userId){service.remove(conversationId,userId);return Result.success();}
 @PutMapping("/members/{userId}/role") public Result<Void> role(@PathVariable Long conversationId,@PathVariable Long userId,@Valid @RequestBody ChatMemberRoleRequest r){service.updateRole(conversationId,userId,r.role());return Result.success();}
 @PostMapping("/trip-confirmations") public Result<Map<String,Object>> createConfirmation(@PathVariable Long conversationId){return Result.success(service.createTripConfirmation(conversationId));}
 @GetMapping("/trip-confirmations/{id}") public Result<Map<String,Object>> confirmation(@PathVariable Long conversationId,@PathVariable Long id){return Result.success(service.confirmationDetails(conversationId,id));}
 @PostMapping("/trip-confirmations/{id}/respond") public Result<Map<String,Object>> respond(@PathVariable Long conversationId,@PathVariable Long id,@Valid @RequestBody TripConfirmationRespondRequest r){return Result.success(service.respondConfirmation(conversationId,id,r));}
 @PostMapping("/trip-confirmations/{id}/start") public Result<Map<String,Object>> start(@PathVariable Long conversationId,@PathVariable Long id){return Result.success(service.startConfirmedTrip(conversationId,id));}
 @PostMapping("/close") public Result<Void> close(@PathVariable Long conversationId){service.close(conversationId);return Result.success();}
}
