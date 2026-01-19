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
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccountRepository accountRepository;
    private final CharacterRepository characterRepository;
    private final CharacterService characterService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.use-firebase-auth:false}")
    private boolean useFirebaseAuth;

    public AccountService(PasswordEncoder passwordEncoder, JwtUtil jwtUtil,AccountRepository accountRepository, CharacterRepository characterRepository, CharacterService characterService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.accountRepository = accountRepository;
        this.characterRepository = characterRepository;
        this.characterService = characterService;
    }

    @Transactional
    public String signUp(SignUpRequest request) {
        // 1. Null 체크
        if (request.getEmail() == null) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_NULL_EMAIL);
        }
        if (request.getPassword() == null) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_NULL_PASSWORD);
        }

        // 2. 빈 문자열 및 공백 체크
        String email = request.getEmail().trim();
        String password = request.getPassword().trim();

        if (email.isEmpty()) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_EMPTY_EMAIL);
        }
        if (password.isEmpty()) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_EMPTY_PASSWORD);
        }

        // 3. 이메일 형식 검증
        if (!isValidEmail(email)) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_INVALID_EMAIL_FORMAT);
        }

        // 4. 이메일 길이 제한 (DB VARCHAR 255)
        if (email.length() > 255) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_EMAIL_TOO_LONG);
        }

        // 5. 패스워드 길이 제한
        if (password.length() < 8) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_PASSWORD_TOO_SHORT);
        }
        if (password.length() > 50) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_PASSWORD_TOO_LONG);
        }

        // 6. 중복 이메일 체크
        if (accountRepository.existsByEmail(email)) {
            throw new BusinessException(ServerErrorCode.ACCOUNT_REGISTER_FAIL_ALREADY_EXIST_EMAIL);
        }

        // 7. 계정 생성 + 기본 캐릭터 자동 생성
        createAccountWithDefaultCharacter(email, password);

        return "Account created successfully";
    }

    // 계정 생성 + 기본 캐릭터 생성 (공통 로직)
    private Account createAccountWithDefaultCharacter(String email, String password) {
        // 1. 계정 생성
        Account account = new Account();
        account.setEmail(email.toLowerCase());
        account.setPassword(passwordEncoder.encode(password));
        Account savedAccount = accountRepository.save(account);

        // 2. 기본 캐릭터 자동 생성
        String defaultCharacterName = generateDefaultCharacterName(email);
        CharacterCreateRequest characterRequest = new CharacterCreateRequest();
        characterRequest.setCharacterName(defaultCharacterName);

        // SecurityContext에 인증 정보 임시 설정
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                email, null, java.util.Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            characterService.createCharacter(characterRequest);
        } finally {
            SecurityContextHolder.clearContext();
        }

        return savedAccount;
    }

    // 이메일로부터 기본 캐릭터 이름 생성
    private String generateDefaultCharacterName(String email) {
        String username = email.split("@")[0];
        // 특수문자 제거 및 길이 제한
        String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitized.length() > 12) {
            sanitized = sanitized.substring(0, 12);
        }

        // 중복 방지를 위해 타임스탬프 추가
        String timestamp = String.valueOf(System.currentTimeMillis() % 10000);
        return sanitized + timestamp;
    }

    // 이메일 형식 검증 메서드
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    public AuthResponse login(LoginRequest request) {
        // 1. Null 체크
        if (request.getEmail() == null) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_NULL_EMAIL);
        }
        if (request.getPassword() == null) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_NULL_PASSWORD);
        }

        // 2. 빈 문자열 체크
        String email = request.getEmail().trim();
        String password = request.getPassword().trim();

        if (email.isEmpty()) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_EMPTY_EMAIL);
        }
        if (password.isEmpty()) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_EMPTY_PASSWORD);
        }

        // 3. 계정 조회
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.LOGIN_FAIL_FIND_BY_EMAIL));

        // 4. 패스워드 검증
        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_MATCH_PASSWORD);
        }

        return AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getEmail(), account.getId()))
                .refreshToken(jwtUtil.createRefreshToken(account.getEmail(), account.getId()))
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        // 1. Null 체크
        if (request.getRefreshToken() == null) {
            throw new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_NULL_TOKEN);
        }

        // 2. 빈 문자열 체크
        String refreshToken = request.getRefreshToken().trim();
        if (refreshToken.isEmpty()) {
            throw new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_EMPTY_TOKEN);
        }

        // 3. 토큰 유효성 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_INVALID_TOKEN);
        }

        // 4. 토큰에서 정보 추출
        String email = jwtUtil.getEmailFromToken(refreshToken);
        Long characterId = jwtUtil.getCharacterIdFromToken(refreshToken);

        // 5. 계정 조회
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_ACCOUNT_NOT_FOUND));

        // 6. 새 토큰 생성
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

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        log.info("Google login attempt with ID token, useFirebaseAuth={}", useFirebaseAuth);

        String uid;
        String email;
        Boolean emailVerified;

        if (useFirebaseAuth) {
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
                log.info("Firebase token verified successfully");
                uid = decodedToken.getUid();
                email = decodedToken.getEmail();
                emailVerified = decodedToken.isEmailVerified();
            } catch (FirebaseAuthException e) {
                throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_FIREBASE_AUTH_EXCEPTION);
            }
        } else {
            try {
                GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                        .setAudience(Collections.singletonList(googleClientId))
                        .build();
                GoogleIdToken idToken = verifier.verify(request.getIdToken());
                if (idToken == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_TOKEN);
                log.info("Google ID token verified successfully");
                Payload payload = idToken.getPayload();
                uid = payload.getSubject();
                email = payload.getEmail();
                emailVerified = payload.getEmailVerified();
            } catch (GeneralSecurityException | IOException e) {
                throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_TOKEN_VERIFICATION_EXCEPTION);
            }
        }

        log.info("User info - email: {}, uid: {}, verified: {}", email, uid, emailVerified);

        if (email == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_EMAIL);
        if (uid == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_UID);
        if (emailVerified == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_EMAIL_VERIFIED);
        if (emailVerified == false) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_EMAIL_VERIFIED);

        // 계정 조회 또는 생성 (신규 계정 시 기본 캐릭터 자동 생성)
        Account account = accountRepository.findByEmail(email)
                .orElseGet(() -> createAccountWithDefaultCharacter(email, uid));

        return AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getEmail(), account.getId()))
                .refreshToken(jwtUtil.createRefreshToken(account.getEmail(), account.getId()))
                .build();
    }

    public boolean validateCharacterOwnership(Long accountId, Long characterId) {
        return characterRepository.findById(characterId)
                .map(character -> character.getAccountId().equals(accountId) && !character.isDeleted())
                .orElse(false);
    }

    public ApiResponse<List<CharacterResponse>> getAllCharacters() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_ALL_CHARACTERS_FAIL_ACCOUNT_NOT_FOUND));

        List<Character> characters = characterRepository.findByAccountId(account.getId());
        List<CharacterResponse> characterResponses = characters.stream()
                .map(character -> CharacterResponse.builder()
                        .characterId(((long) 1 << 56) | character.getId())
                        .characterName(character.getCharacterName())
                        .build())
                .collect(Collectors.toList());

        return ApiResponse.success(characterResponses);
    }

}
