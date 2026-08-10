// 보상코드 입력 처리 서비스 — 코드 텍스트를 파싱해 해당 보상을 지급하고 중복 사용을 방지
package com.bk.sbs.service;

import com.bk.sbs.dto.CommanderInfoDto;
import com.bk.sbs.dto.RedeemCodeResponse;
import com.bk.sbs.entity.RedeemCodeUsage;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.RedeemCodeUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RedeemCodeService {

    // 테스트용: 커맨더 레벨을 즉시 N으로 설정 (이미 N 이상이면 무시)
    private static final Pattern COMMANDER_LEVEL_CODE_PATTERN = Pattern.compile("^comlevel(\\d+)$");

    private final RedeemCodeUsageRepository redeemCodeUsageRepository;
    private final ZoneService zoneService;
    private final CommanderService commanderService;

    public RedeemCodeService(RedeemCodeUsageRepository redeemCodeUsageRepository, ZoneService zoneService, CommanderService commanderService) {
        this.redeemCodeUsageRepository = redeemCodeUsageRepository;
        this.zoneService = zoneService;
        this.commanderService = commanderService;
    }

    @Transactional
    public RedeemCodeResponse redeem(Long commanderId, String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty())
            throw new BusinessException(ServerErrorCode.REDEEM_CODE_FAIL_EMPTY_CODE);

        String code = rawCode.trim().toLowerCase();

        if (redeemCodeUsageRepository.existsByCommanderIdAndCode(commanderId, code))
            throw new BusinessException(ServerErrorCode.REDEEM_CODE_FAIL_ALREADY_USED);

        applyCodeEffect(commanderId, code);

        redeemCodeUsageRepository.save(new RedeemCodeUsage(commanderId, code));

        CommanderInfoDto info = commanderService.getCommanderInfoDto(commanderId);
        return RedeemCodeResponse.builder()
                .commanderLevel(info.getCommanderLevel())
                .exp(info.getExp())
                .build();
    }

    // 코드 텍스트를 해석해 해당 보상 효과를 적용 — 새 코드 종류가 늘어나면 이 안에 분기 추가
    private void applyCodeEffect(Long commanderId, String code) {
        Matcher commanderLevelMatcher = COMMANDER_LEVEL_CODE_PATTERN.matcher(code);
        if (commanderLevelMatcher.matches() == true)
        {
            int targetLevel = Integer.parseInt(commanderLevelMatcher.group(1));
            zoneService.setCommanderLevelAtLeast(commanderId, targetLevel);
            return;
        }

        throw new BusinessException(ServerErrorCode.REDEEM_CODE_FAIL_INVALID_CODE);
    }
}
