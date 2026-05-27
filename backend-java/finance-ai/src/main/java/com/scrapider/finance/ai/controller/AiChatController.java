package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.AiChatParam;
import com.scrapider.finance.ai.domain.vo.AiChatVO;
import com.scrapider.finance.ai.service.AiChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatVO> chat(@RequestBody AiChatParam param) {
        return ResponseEntity.ok(this.aiChatService.chat(param));
    }
}
