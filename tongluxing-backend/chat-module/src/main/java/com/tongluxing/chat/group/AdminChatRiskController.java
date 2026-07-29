package com.tongluxing.chat.group;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 负责管理端聊天风控相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/chat-risk")
public class AdminChatRiskController {
 private final ChatGroupService service;
 /** 查询待处理或已处理的聊天举报记录。 */
 @GetMapping("/reports") public Result<List<Map<String,Object>>> reports(@RequestParam(defaultValue="PENDING")String status,@RequestParam(defaultValue="100")Integer limit){return Result.success(service.reports(status,limit));}
 /** 查询消息风控命中记录。 */
 @GetMapping("/message-risks") public Result<List<Map<String,Object>>> risks(@RequestParam(defaultValue="PENDING")String status,@RequestParam(defaultValue="100")Integer limit){return Result.success(service.risks(status,limit));}
 /** 查询行程确认单及其处理状态。 */
 @GetMapping("/trip-confirmations") public Result<List<Map<String,Object>>> confirmations(@RequestParam(defaultValue="OPEN")String status,@RequestParam(defaultValue="100")Integer limit){return Result.success(service.confirmations(status,limit));}
 /** 审核待处理记录，并持久化审核结论。 */
 @PostMapping("/reports/{id}/review") public Result<Map<String,Object>> review(@PathVariable Long id,@Valid @RequestBody ChatRiskReviewRequest r){return Result.success(service.review(id,r));}
}
