// 모든 인증된 API 호출에서 lastOnlineAt을 갱신 — 온라인/오프라인 자원 계산 기준
package com.bk.sbs.service;

import com.bk.sbs.repository.CharacterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OnlineActivityService {

    private static final long THROTTLE_SECONDS = 30L;

    private final CharacterRepository characterRepository;

    public OnlineActivityService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    // 요청 완료 후 호출 — 30초 스로틀로 lastOnlineAt 갱신 (별도 트랜잭션)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(Long characterId) {
        Instant now = Instant.now();
        characterRepository.updateLastOnlineAtIfStale(characterId, now, now.minusSeconds(THROTTLE_SECONDS));
    }
}
