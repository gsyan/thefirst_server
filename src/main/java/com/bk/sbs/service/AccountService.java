//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Account;
import com.bk.sbs.entity.Character;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.AccountRepository;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccountRepository accountRepository;
    private final CharacterRepository characterRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.use-firebase-auth:false}")
    private boolean useFirebaseAuth;

    public AccountService(PasswordEncoder passwordEncoder, JwtUtil jwtUtil,AccountRepository accountRepository, CharacterRepository characterRepository) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.accountRepository = accountRepository;
        this.characterRepository = characterRepository;
    }

    public String signUp(SignUpRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_REASON1);
        }
        Account account = new Account();
        account.setEmail(request.getEmail());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        Account savedAccount = accountRepository.save(account);
        return "Account created successfully";
    }

    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.LOGIN_FAIL_REASON1));

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_REASON1);
        }

        return AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getEmail(), account.getId()))
                .refreshToken(jwtUtil.createRefreshToken(account.getEmail(), account.getId()))
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ServerErrorCode.INVALID_TOKEN);
        }

        String email = jwtUtil.getEmailFromToken(refreshToken);
        Long characterId = jwtUtil.getCharacterIdFromToken(refreshToken);

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACCOUNT_NOT_FOUND));

        AuthResponse response = AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(email, account.getId()))
                .refreshToken(jwtUtil.createRefreshToken(email, account.getId()))
                .build();

        if (characterId != null) {
            response.setAccessToken(jwtUtil.createAccessTokenWithCharacter(email, account.getId(), characterId));
            response.setRefreshToken(jwtUtil.createRefreshTokenWithCharacter(email, account.getId(), characterId));
        }

        return response;
    }

    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            log.info("Google login attempt with ID token, useFirebaseAuth={}", useFirebaseAuth);

            String uid;
            String email;
            Boolean emailVerified;

            if (useFirebaseAuth) {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
                log.info("Firebase token verified successfully");
                uid = decodedToken.getUid();
                email = decodedToken.getEmail();
                emailVerified = decodedToken.isEmailVerified();
            } else {
                GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
                GoogleIdToken idToken = verifier.verify(request.getIdToken());
                if (idToken == null)
                    throw new BusinessException(ServerErrorCode.LOGIN_FAIL_REASON1);
                log.info("Google ID token verified successfully");
                Payload payload = idToken.getPayload();
                uid = payload.getSubject();
                email = payload.getEmail();
                emailVerified = payload.getEmailVerified();
            }

            log.info("User info - email: {}, uid: {}, verified: {}", email, uid, emailVerified);

            if (email == null || uid == null)
                throw new BusinessException(ServerErrorCode.LOGIN_FAIL_REASON1);

            if (emailVerified == null || emailVerified == false)
                throw new BusinessException(ServerErrorCode.LOGIN_FAIL_REASON1);

            Account account = accountRepository.findByEmail(email)
                    .orElseGet(() -> {
                        Account newAccount = new Account();
                        newAccount.setEmail(email);
                        newAccount.setPassword(passwordEncoder.encode(uid));
                        return accountRepository.save(newAccount);
                    });

            return AuthResponse.builder()
                    .accessToken(jwtUtil.createAccessToken(account.getEmail(), account.getId()))
                    .refreshToken(jwtUtil.createRefreshToken(account.getEmail(), account.getId()))
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_REASON1);
        }
    }

    public boolean validateCharacterOwnership(Long accountId, Long characterId) {
        return characterRepository.findById(characterId)
                .map(character -> character.getAccountId().equals(accountId) && !character.isDeleted())
                .orElse(false);
    }

    public ApiResponse<List<CharacterResponse>> getAllCharacters() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Account account = accountRepository.findByEmail(email)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.ACCOUNT_NOT_FOUND));

            List<Character> characters = characterRepository.findByAccountId(account.getId());
            List<CharacterResponse> characterResponses = characters.stream()
                    .map(character -> CharacterResponse.builder()
                            .characterId(((long) 1 << 56) | character.getId())
                            .characterName(character.getCharacterName())
                            .build())
                    .collect(Collectors.toList());

            return ApiResponse.success(characterResponses);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getErrorCode());
        } catch (Exception e) {
            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }

    private ServerErrorCode determineErrorCode(String message) {
        if (message.contains("Account not found")) {
            return ServerErrorCode.LOGIN_FAIL_REASON1;
        }
        return ServerErrorCode.SUCCESS;
    }

}
