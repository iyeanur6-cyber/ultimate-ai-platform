package ai.ultimate.security.auth;

import ai.ultimate.security.auth.request.LoginRequest;
import ai.ultimate.security.auth.request.RegisterRequest;
import ai.ultimate.security.auth.response.RegisterResponse;
import ai.ultimate.security.auth.response.TokenResponse;
import ai.ultimate.security.jwt.JwtService;
import ai.ultimate.user.User;
import ai.ultimate.user.UserMapper;
import ai.ultimate.user.UserRepository;
import ai.ultimate.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    // ── Register ──────────────────────────────────

    public Mono<RegisterResponse> register(
            RegisterRequest request) {

        return userRepository
                .existsByUsername(request.username())
                .flatMap(usernameTaken -> {
                    if (usernameTaken) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Username '" + request.username()
                                                + "' is already taken"
                                )
                        );
                    }
                    return userRepository
                            .existsByEmail(request.email());
                })
                .flatMap(emailTaken -> {
                    if (emailTaken) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Email is already registered"
                                )
                        );
                    }
                    return userRepository.count();
                })
                .flatMap(count -> {
                    UserRole role = count == 0
                            ? UserRole.ADMIN
                            : UserRole.USER;

                    User partial = userMapper.toEntity(request);

                    User user = User.create(
                            UUID.randomUUID(),
                            partial.username(),
                            partial.email(),
                            passwordEncoder.encode(
                                    request.password()),
                            partial.displayName(),
                            role
                    );

                    // ← USE insert() NOT save()
                    // insert() always does INSERT
                    // save() does UPDATE when ID is not null
                    return r2dbcEntityTemplate.insert(user);
                })
                .map(saved -> {
                    log.info(
                            "User registered: username={} role={}",
                            saved.username(), saved.role()
                    );
                    return userMapper.toRegisterResponse(saved);
                });
    }

    // ── Login ─────────────────────────────────────

    public Mono<TokenResponse> login(LoginRequest request) {

        return userRepository
                .findByUsername(request.username())
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid username or password"
                        )
                ))
                .flatMap(user -> {
                    if (!user.active()) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Account is disabled"
                                )
                        );
                    }

                    if (!passwordEncoder.matches(
                            request.password(),
                            user.passwordHash())) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Invalid username or password"
                                )
                        );
                    }

                    try {
                        String accessToken =
                                jwtService.generateAccessToken(user);
                        String refreshToken =
                                jwtService.generateRefreshToken(user);
                        TokenResponse.UserInfo userInfo =
                                userMapper.toUserInfo(user);
                        TokenResponse response = TokenResponse.of(
                                accessToken,
                                refreshToken,
                                jwtService.getAccessTokenExpirySeconds(),
                                userInfo
                        );
                        return Mono.just(response);

                    } catch (Exception e) {
                        log.error(
                                "Login token build failed: {}",
                                e.getMessage(), e);
                        return Mono.error(e);
                    }
                });
    }
}