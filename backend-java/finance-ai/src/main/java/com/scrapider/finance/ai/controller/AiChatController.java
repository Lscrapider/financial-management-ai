package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.AiChatParam;
import com.scrapider.finance.ai.domain.vo.AiChatWebSocketTicketVO;
import com.scrapider.finance.ai.domain.vo.AiChatVO;
import com.scrapider.finance.ai.domain.vo.AiChatConversationMessagesVO;
import com.scrapider.finance.ai.service.AiChatConversationService;
import com.scrapider.finance.ai.service.AiChatService;
import com.scrapider.finance.ai.service.AiChatWebSocketTicketService;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.security.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatWebSocketTicketService aiChatWebSocketTicketService;
    private final AiChatConversationService aiChatConversationService;

    public AiChatController(
            AiChatService aiChatService,
            AiChatWebSocketTicketService aiChatWebSocketTicketService,
            AiChatConversationService aiChatConversationService) {
        this.aiChatService = aiChatService;
        this.aiChatWebSocketTicketService = aiChatWebSocketTicketService;
        this.aiChatConversationService = aiChatConversationService;
    }

    @Deprecated
    @PostMapping("/chat")
    public ResponseEntity<AiChatVO> chat(@RequestBody AiChatParam param) {
        return ResponseEntity.ok(this.aiChatService.chat(param));
    }

    @PostMapping("/chat/ws-ticket")
    public ApiResponseVO<AiChatWebSocketTicketVO> issueWebSocketTicket(
            @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponseVO.success(this.aiChatWebSocketTicketService.issue(loginUser));
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponseVO<AiChatConversationMessagesVO>> listMessages(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String conversationId,
            @RequestParam(required = false) Long beforeId) {
        Long userId = loginUser.getUser().getId();
        return this.aiChatConversationService.listMessages(userId, conversationId, beforeId)
                .map(data -> ResponseEntity.ok(ApiResponseVO.success(data)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseVO.<AiChatConversationMessagesVO>error("对话不存在。")));
    }
}
