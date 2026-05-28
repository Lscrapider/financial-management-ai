package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.WatchGroupItemSaveParam;
import com.scrapider.finance.domain.param.WatchGroupSaveParam;
import com.scrapider.finance.domain.vo.WatchGroupItemVO;
import com.scrapider.finance.domain.vo.WatchGroupVO;
import com.scrapider.finance.security.LoginUser;
import java.util.List;

public interface WatchPoolService {

    List<WatchGroupVO> listGroups(LoginUser loginUser);

    WatchGroupVO saveGroup(LoginUser loginUser, WatchGroupSaveParam param);

    void deleteGroup(LoginUser loginUser, Long id);

    WatchGroupItemVO saveItem(LoginUser loginUser, WatchGroupItemSaveParam param);

    void deleteItem(LoginUser loginUser, Long id);
}
