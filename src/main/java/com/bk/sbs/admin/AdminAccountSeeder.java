//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin;

import com.bk.sbs.entity.Account;
import com.bk.sbs.enums.nogenerated.EAccountRole;
import com.bk.sbs.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

// 어드민 계정이 하나도 없으면 기본 계정을 1회 생성 — /admin은 SSH 터널 뒤에만 있어 인터넷에 노출되지 않으므로 낮은 리스크로 판단.
// AppConfigInitializer와 동일하게 "없을 때만 생성"이라 비밀번호를 바꾼 뒤 재기동해도 되돌아가지 않음
@Component
@Slf4j
public class AdminAccountSeeder {

    @Value("${admin.seed.email:admin}")
    private String seedEmail;

    @Value("${admin.seed.password:admin}")
    private String seedPassword;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountSeeder(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        boolean alreadyExists = accountRepository.findByEmail(seedEmail).isPresent();
        if (alreadyExists == true) return;

        Account account = new Account();
        account.setEmail(seedEmail);
        account.setPassword(passwordEncoder.encode(seedPassword));
        account.setRole(EAccountRole.ADMIN.name());
        account.setDeleted(false);
        account.setDateTime(Instant.now());
        accountRepository.save(account);

        log.info("[AdminAccountSeeder] 기본 어드민 계정 생성: email={}", seedEmail);
    }
}
