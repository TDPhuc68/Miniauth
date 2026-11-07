package com.miniauth.infrastructure.idp;

import com.miniauth.core.exception.AccountLockedException;
import com.miniauth.core.exception.InvalidGrantException;
import com.miniauth.core.model.AuthenticationRequest;
import com.miniauth.core.model.User;
import com.miniauth.core.port.AuthenticationProvider;
import com.miniauth.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Hiện thực chiến lược xác thực với cơ sở dữ liệu nội bộ (Local Database).
 * Sử dụng BCrypt để so khớp mật khẩu và tích hợp cơ chế phòng thủ Brute-force
 * tự động khóa tài khoản sau số lần nhập sai cấu hình.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${miniauth.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${miniauth.security.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    @Override
    public String getProviderType() {
        return "LOCAL";
    }

    @Override
    public Mono<User> authenticate(AuthenticationRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return Mono.error(new InvalidGrantException("Username and password must not be empty"));
        }

        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new InvalidGrantException("Invalid username or password")))
                .flatMap(user -> {
                    // 1. Kiểm tra tài khoản có đang bị khóa hay không
                    if (user.isLocked()) {
                        log.warn("Login attempt for locked account: {}", user.getUsername());
                        return Mono.error(new AccountLockedException("Account is temporarily locked. Try again later."));
                    }

                    // 2. Đối chiếu mật khẩu đã hash bằng BCrypt
                    if (passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        log.info("User authenticated successfully: {}", user.getUsername());
                        // Nếu trước đó từng nhập sai, reset lại trạng thái bình thường
                        if (user.getFailedAttempts() != null && user.getFailedAttempts() > 0) {
                            user.setFailedAttempts(0);
                            user.setStatus("ACTIVE");
                            user.setLockedUntil(null);
                            return userRepository.save(user);
                        }
                        return Mono.just(user);
                    } else {
                        // 3. Mật khẩu không đúng: Tăng failedAttempts và khóa nếu vượt ngưỡng
                        int failed = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
                        user.setFailedAttempts(failed);

                        if (failed >= maxFailedAttempts) {
                            user.setStatus("LOCKED");
                            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
                            log.warn("Account {} locked due to {} failed attempts", user.getUsername(), failed);

                            return userRepository.save(user)
                                    .then(Mono.error(new AccountLockedException(
                                            "Account locked due to too many failed login attempts"
                                    )));
                        }

                        log.warn("Invalid password for user {}. Failed attempts: {}/{}", user.getUsername(), failed, maxFailedAttempts);
                        return userRepository.save(user)
                                .then(Mono.error(new InvalidGrantException("Invalid username or password")));
                    }
                });
    }
}
