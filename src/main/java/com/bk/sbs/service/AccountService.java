//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Account;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.*;
import com.bk.sbs.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccountRepository accountRepository;
    private final CommanderRepository commanderRepository;
    private final CommanderService commanderService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired private VipSubscriptionRepository vipSubscriptionRepository;
    @Autowired private PvpRecordRepository pvpRecordRepository;
    @Autowired private ClearedZoneRepository clearedZoneRepository;
    @Autowired private ShipModuleRepository shipModuleRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private FleetRepository fleetRepository;
    @Autowired private ProgressRepository progressRepository;
    @Autowired private RedisService redisService;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.use-firebase-auth:false}")
    private boolean useFirebaseAuth;

    @Value("${test.guest-keep-data-on-logout:false}")
    private boolean guestKeepDataOnLogout;

    public AccountService(PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AccountRepository accountRepository, CommanderRepository commanderRepository, CommanderService commanderService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.accountRepository = accountRepository;
        this.commanderRepository = commanderRepository;
        this.commanderService = commanderService;
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
        createAccountWithDefaultCommander(email, password);

        return "Account created successfully";
    }

    // 계정 생성 + 기본 커맨더 생성 (공통 로직)
    private Account createAccountWithDefaultCommander(String email, String password) {
        // 1. 계정 생성
        Account account = new Account();
        account.setEmail(email.toLowerCase());
        account.setPassword(passwordEncoder.encode(password));
        Account savedAccount = accountRepository.save(account);

        log.info("createAccountWithDefaultCommander: accountId={}, email={}", savedAccount.getId(), savedAccount.getEmail());
        // 2. 기본 커맨더 자동 생성 (이름은 null → CommanderService에서 commander_+id로 설정)
        CommanderCreateRequest commanderRequest = new CommanderCreateRequest();

        // SecurityContext에 accountId로 임시 인증 설정
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                savedAccount.getId().toString(), null, java.util.Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            commanderService.createCommander(commanderRequest);
        } finally {
            SecurityContextHolder.clearContext();
        }

        return savedAccount;
    }

    // 새 리프레시 토큰의 jti를 활성 세션으로 등록. Redis 장애 시에도 로그인 자체는 막지 않고 로그만 남김
    public void registerSession(Long accountId, String refreshToken) {
        try {
            String jti = jwtUtil.getJtiFromToken(refreshToken);
            if (jti != null) {
                redisService.setActiveJti(accountId, jti, jwtUtil.getRefreshTokenValidity());
            }
        } catch (Exception e) {
            log.error("리프레시 토큰 세션 등록 실패: accountId={}", accountId, e);
        }
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

        String refreshToken = jwtUtil.createRefreshToken(account.getId());
        registerSession(account.getId(), refreshToken);

        return AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getId()))
                .refreshToken(refreshToken)
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

        // 4. 토큰에서 accountId 추출 (구 email subject 토큰이면 파싱 실패)
        Long accountId;
        Long commanderId;
        try {
            accountId = jwtUtil.getAccountIdFromSubject(refreshToken);
            commanderId = jwtUtil.getCommanderIdFromToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_INVALID_TOKEN2);
        }


        // 5. 계정 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_ACCOUNT_NOT_FOUND));

        boolean bGoogleLinked = isGoogleLinked(account.getId());

        // 6. jti 회전/재사용 검증. 유예 응답이면 새 jti를 발급하지 않고 현재 활성 jti를 그대로 재사용
        String presentedJti = jwtUtil.getJtiFromToken(refreshToken);
        log.info("[임시로그] refreshToken 진입: accountId={} presentedJti={} 요청시각={}", accountId, presentedJti, java.time.Instant.now());
        String reusedActiveJti = resolveJtiForRotation(accountId, presentedJti);

        String newJti;
        if (reusedActiveJti != null) {
            newJti = reusedActiveJti;
        } else {
            newJti = UUID.randomUUID().toString();
            rotateSession(accountId, presentedJti, newJti);
        }

        // 7. 새 토큰 생성
        AuthResponse response = AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getId()))
                .refreshToken(jwtUtil.createRefreshTokenWithJti(account.getId(), newJti))
                .bGoogleLinked(bGoogleLinked)
                .build();

        if (commanderId != null) {
            response.setAccessToken(jwtUtil.createAccessTokenWithCommander(account.getId(), commanderId));
            response.setRefreshToken(jwtUtil.createRefreshTokenWithCommanderAndJti(account.getId(), commanderId, newJti));
        }

        return response;
    }

    // 제출된 jti가 유예 기간 중인 구 jti면 현재 활성 jti를 반환(재발급 없이 세션 유지), 아니면 null
    private String resolveJtiForRotation(Long accountId, String presentedJti) {
        String activeJti;
        try {
            activeJti = redisService.getActiveJti(accountId);
        } catch (Exception e) {
            log.error("[임시로그] 리프레시 jti 조회 실패, 검증을 건너뜀: accountId={}", accountId, e);
            return null;
        }
        log.info("[임시로그] resolveJtiForRotation: accountId={} presentedJti={} activeJti(redis)={}", accountId, presentedJti, activeJti);

        // 구버전 토큰(jti 없음) 또는 아직 세션이 등록되지 않은 경우 → 검증 없이 통과 (마이그레이션 허용)
        if (activeJti == null) {
            log.info("[임시로그] resolveJtiForRotation 분기=activeJti가 null(마이그레이션 허용, 검증 통과): accountId={}", accountId);
            return null;
        }

        if (activeJti.equals(presentedJti)) {
            log.info("[임시로그] resolveJtiForRotation 분기=presentedJti == activeJti(정상, 회전 진행): accountId={}", accountId);
            return null;
        }

        boolean inGrace;
        try {
            inGrace = redisService.isJtiInGrace(accountId, presentedJti);
        } catch (Exception e) {
            log.error("[임시로그] 리프레시 jti 유예 조회 실패, 검증을 건너뜀: accountId={}", accountId, e);
            return null;
        }
        log.info("[임시로그] resolveJtiForRotation: accountId={} presentedJti가 activeJti와 다름, inGrace={}", accountId, inGrace);

        if (inGrace == true) {
            // 응답 유실로 인한 재시도로 판단 → 세션 유지, 최신 jti를 다시 내려줌
            log.info("[임시로그] resolveJtiForRotation 분기=유예기간 내 재시도로 판단, 세션 유지: accountId={}", accountId);
            return activeJti;
        }

        // 유예 기간도 지난 구 jti가 옴 → 탈취 의심, 전체 세션 폐기
        log.warn("[임시로그] 리프레시 토큰 재사용 감지, 전체 세션 폐기: accountId={} presentedJti={} activeJti={}", accountId, presentedJti, activeJti);
        try {
            redisService.revokeAllSessions(accountId);
        } catch (Exception e) {
            log.error("[임시로그] 재사용 감지 후 세션 폐기 실패: accountId={}", accountId, e);
        }
        throw new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_REUSE_DETECTED);
    }

    // 구 jti를 유예 상태로 남기고 새 jti를 활성 세션으로 등록
    private void rotateSession(Long accountId, String oldJti, String newJti) {
        try {
            redisService.markJtiInGrace(accountId, oldJti);
            redisService.setActiveJti(accountId, newJti, jwtUtil.getRefreshTokenValidity());
        } catch (Exception e) {
            log.error("리프레시 세션 회전 실패: accountId={}", accountId, e);
        }
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        log.info("Google login attempt with ID token, useFirebaseAuth={}", useFirebaseAuth);

        String uid;
        String googleEmail;
        Boolean emailVerified;

        if (useFirebaseAuth) {
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
                log.info("Firebase token verified successfully");
                uid = decodedToken.getUid();
                googleEmail = decodedToken.getEmail();
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
                googleEmail = payload.getEmail();
                emailVerified = payload.getEmailVerified();
            } catch (GeneralSecurityException | IOException e) {
                throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_TOKEN_VERIFICATION_EXCEPTION);
            }
        }

        log.info("User info - uid: {}, verified: {}", uid, emailVerified);

        if (googleEmail == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_EMAIL);
        if (uid == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_UID);
        if (emailVerified == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_EMAIL_VERIFIED);
        if (emailVerified == false) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_EMAIL_VERIFIED);

        // googleId로 조회 → 없으면 신규 생성 (email은 googlelink_uid 형식으로 저장)
        Account account = accountRepository.findByGoogleId(uid).orElseGet(() ->
            createAccountWithDefaultCommander("googlelink_" + uid, uid)
        );

        if (account.getGoogleId() == null) {
            account.setGoogleId(uid);
            accountRepository.save(account);
        }

        String refreshToken = jwtUtil.createRefreshToken(account.getId());
        registerSession(account.getId(), refreshToken);

        return AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getId()))
                .refreshToken(refreshToken)
                .bGoogleLinked(true)
                .build();
    }

    // 현재 로그인된 계정에 구글 계정 연동 — email을 googlelink_uid 형식으로 변경
    @Transactional
    public AuthResponse linkGoogle(LinkGoogleRequest request) {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_ACCOUNT_NOT_FOUND));

        String uid;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_NULL_TOKEN);
            uid = idToken.getPayload().getSubject();
        } catch (GeneralSecurityException | IOException e) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GOOGLE_TOKEN_VERIFICATION_EXCEPTION);
        }

        if (account.getGoogleId() != null && account.getGoogleId().equals(uid))
            throw new BusinessException(ServerErrorCode.LINK_GOOGLE_FAIL_ALREADY_LINKED);
        if (accountRepository.existsByGoogleId(uid))
            throw new BusinessException(ServerErrorCode.LINK_GOOGLE_FAIL_GOOGLE_ID_ALREADY_USED);

        account.setGoogleId(uid);
        account.setEmail("googlelink_" + uid);
        accountRepository.save(account);
        log.info("Google account linked for accountId={}", account.getId());

        // subject = accountId(불변)이므로 기존 토큰 그대로 유효, 재발급 불필요
        return AuthResponse.builder()
                .bGoogleLinked(true)
                .build();
    }

    // 구글 연동 해제 — 게스트 계정으로 전환 (email: guest_newUuid)
    @Transactional
    public UnlinkGoogleResponse unlinkGoogle() {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_ACCOUNT_NOT_FOUND));

        if (account.getGoogleId() == null)
            throw new BusinessException(ServerErrorCode.UNLINK_GOOGLE_FAIL_NOT_LINKED);

        account.setGoogleId(null);

        String guestId;
        if (account.getEmail().startsWith("guest_")) {
            guestId = account.getEmail().substring(6);
        } else {
            // googlelink_ 또는 기타 → 새 게스트 계정으로 전환
            guestId = UUID.randomUUID().toString();
            account.setEmail("guest_" + guestId);
        }

        accountRepository.save(account);
        log.info("Google account unlinked for accountId={}", account.getId());

        return UnlinkGoogleResponse.builder()
                .guestId(guestId)
                .build();
    }

    // 계정의 구글 연동 여부 조회
    public boolean isGoogleLinked(Long accountId) {
        return accountRepository.findById(accountId)
                .map(account -> account.getGoogleId() != null)
                .orElse(false);
    }

    @Transactional
    public AuthResponse guestLogin(GuestLoginRequest request) {
        log.info("Guest login attempt with guestId: {}", request.getGuestId());

        if (request.getGuestId() == null || request.getGuestId().trim().isEmpty()) {
            throw new BusinessException(ServerErrorCode.LOGIN_FAIL_GUEST_NULL_ID);
        }

        // guestId를 email 형식으로 변환 (기존 DB 구조 활용)
        String guestEmail = "guest_" + request.getGuestId();

        // 계정 조회 또는 생성 (신규 계정 시 기본 캐릭터 자동 생성)
        Account account = accountRepository.findByEmail(guestEmail)
                .orElseGet(() -> {
                    log.info("Creating new guest account with guestId: {}", request.getGuestId());
                    return createAccountWithDefaultCommander(guestEmail, request.getGuestId());
                });

        String refreshToken = jwtUtil.createRefreshToken(account.getId());
        registerSession(account.getId(), refreshToken);

        return AuthResponse.builder()
                .accessToken(jwtUtil.createAccessToken(account.getId()))
                .refreshToken(refreshToken)
                .build();
    }

    // 로그아웃 — 현재 계정의 활성 리프레시 토큰 세션을 즉시 폐기
    public void logout() {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        try {
            redisService.revokeAllSessions(accountId);
        } catch (Exception e) {
            log.error("로그아웃 시 세션 폐기 실패: accountId={}", accountId, e);
        }
        log.info("Account logged out: accountId={}", accountId);
    }

    @Transactional
    public void deleteAccount() {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REFRESH_TOKEN_FAIL_ACCOUNT_NOT_FOUND));

        if (guestKeepDataOnLogout == true) {
            // 테스트 모드: 데이터 보존, 클라이언트는 GuestId만 지우고 재접속 시 새 계정 생성
            log.info("Account data preserved (test.guest-keep-data-on-logout=true): accountId={}", accountId);
            return;
        }

        List<Commander> commanders = commanderRepository.findByAccountId(accountId);
        for (Commander commander : commanders) {
            Long commanderId = commander.getId();
            // FK 순서 준수: 말단 테이블부터 삭제
            vipSubscriptionRepository.deleteByCommanderId(commanderId);
            pvpRecordRepository.deleteByCommanderId(commanderId);
            clearedZoneRepository.deleteByCommanderId(commanderId);
            shipModuleRepository.deleteByCommanderId(commanderId);
            shipRepository.deleteByCommanderId(commanderId);
            fleetRepository.deleteByCommanderId(commanderId);
            progressRepository.deleteByCommanderId(commanderId);
        }
        commanderRepository.deleteByAccountId(accountId);
        accountRepository.delete(account);

        try {
            redisService.revokeAllSessions(accountId);
        } catch (Exception e) {
            log.error("계정 삭제 시 세션 폐기 실패: accountId={}", accountId, e);
        }

        log.info("Account hard-deleted: accountId={}", accountId);
    }

    public boolean validateCommanderOwnership(Long accountId, Long commanderId) {
        return commanderRepository.findById(commanderId)
                .map(commander -> commander.getAccountId().equals(accountId) && !commander.isDeleted())
                .orElse(false);
    }

    public ApiResponse<List<CommanderResponse>> getAllCommanders() {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_ALL_COMMANDERS_FAIL_ACCOUNT_NOT_FOUND));

        List<Commander> commanders = commanderRepository.findByAccountId(account.getId());
        List<CommanderResponse> commanderResponses = commanders.stream()
                .map(commander -> CommanderResponse.builder()
                        .commanderId(((long) 1 << 56) | commander.getId())
                        .commanderName(commander.getCommanderName())
                        .build())
                .collect(Collectors.toList());

        return ApiResponse.success(commanderResponses);
    }

}



