package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.AiChatParam;
import com.scrapider.finance.ai.domain.vo.AiChatWebSocketTicketVO;
import com.scrapider.finance.ai.domain.vo.AiChatVO;
import com.scrapider.finance.ai.service.AiChatService;
import com.scrapider.finance.ai.service.AiChatWebSocketTicketService;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.security.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatWebSocketTicketService aiChatWebSocketTicketService;

    public AiChatController(
            AiChatService aiChatService,
            AiChatWebSocketTicketService aiChatWebSocketTicketService) {
        this.aiChatService = aiChatService;
        this.aiChatWebSocketTicketService = aiChatWebSocketTicketService;
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
}
