package com.tongluxing.chat.group;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/chat-risk")
public class AdminChatRiskController {
 private final ChatGroupService service;
 @GetMapping("/reports") public Result<List<Map<String,Object>>> reports(@RequestParam(defaultValue="PENDING")String status,@RequestParam(defaultValue="100")Integer limit){return Result.success(service.reports(status,limit));}
 @GetMapping("/message-risks") public Result<List<Map<String,Object>>> risks(@RequestParam(defaultValue="PENDING")String status,@RequestParam(defaultValue="100")Integer limit){return Result.success(service.risks(status,limit));}
 @GetMapping("/trip-confirmations") public Result<List<Map<String,Object>>> confirmations(@RequestParam(defaultValue="OPEN")String status,@RequestParam(defaultValue="100")Integer limit){return Result.success(service.confirmations(status,limit));}
 @PostMapping("/reports/{id}/review") public Result<Map<String,Object>> review(@PathVariable Long id,@Valid @RequestBody ChatRiskReviewRequest r){return Result.success(service.review(id,r));}
}
