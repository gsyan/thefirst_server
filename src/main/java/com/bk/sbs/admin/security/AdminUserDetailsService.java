//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin.security;

import com.bk.sbs.entity.Account;
import com.bk.sbs.enums.nogenerated.EAccountRole;
import com.bk.sbs.repository.AccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AdminUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Account account = accountRepository.findByEmail(email).orElse(null);
        boolean isAdmin = account != null
                && account.isDeleted() == false
                && EAccountRole.ADMIN.name().equals(account.getRole());

        if (isAdmin == false) {
            throw new UsernameNotFoundException("관리자 계정이 아닙니다");
        }

        return new User(account.getEmail(), account.getPassword(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
