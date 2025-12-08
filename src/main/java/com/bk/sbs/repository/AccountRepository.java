//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    // 이메일로 계정 조회
    Optional<Account> findByEmail(String email);
    Optional<Account> findById(Long accountId);
    boolean existsByEmail(String email);
}
