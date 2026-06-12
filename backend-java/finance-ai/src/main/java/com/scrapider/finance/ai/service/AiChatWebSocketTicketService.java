package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.AiChatWebSocketTicketVO;
import com.scrapider.finance.security.LoginUser;
import java.util.Optional;

public interface AiChatWebSocketTicketService {

    AiChatWebSocketTicketVO issue(LoginUser loginUser);

    Optional<LoginUser> consume(String ticket);
}
