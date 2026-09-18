//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.entity.CommanderLoginLog;
import com.bk.sbs.repository.CommanderLoginLogRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginLogService {

    private final CommanderLoginLogRepository commanderLoginLogRepository;

    public LoginLogService(CommanderLoginLogRepository commanderLoginLogRepository) {
        this.commanderLoginLogRepository = commanderLoginLogRepository;
    }

    public void record(Long accountId, Long commanderId, String loginType) {
        commanderLoginLogRepository.save(new CommanderLoginLog(accountId, commanderId, loginType));
    }
}
