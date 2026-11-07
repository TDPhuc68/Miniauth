package com.miniauth.infrastructure.idp;

import com.miniauth.core.exception.AccountLockedException;
import com.miniauth.core.exception.InvalidGrantException;
import com.miniauth.core.model.AuthenticationRequest;
import com.miniauth.core.model.User;
import com.miniauth.core.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseAuthenticationProviderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DatabaseAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DatabaseAuthenticationProvider(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(provider, "maxFailedAttempts", 3);
        ReflectionTestUtils.setField(provider, "lockoutDurationMinutes", 15);
    }

    @Test
    @DisplayName("Should authenticate successfully with correct credentials and reset failed attempts")
    void shouldAuthenticateSuccessfully() {
        User user = User.builder()
                .id("u-1")
                .username("john_doe")
                .passwordHash("hashed_pass")
                .status("ACTIVE")
                .failedAttempts(2)
                .build();

        when(userRepository.findByUsername("john_doe")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("password123", "hashed_pass")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("john_doe")
                .password("password123")
                .build();

        StepVerifier.create(provider.authenticate(request))
                .assertNext(authenticatedUser -> {
                    assertThat(authenticatedUser.getUsername()).isEqualTo("john_doe");
                    assertThat(authenticatedUser.getFailedAttempts()).isZero();
                })
                .verifyComplete();

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should fail when user does not exist")
    void shouldFailWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Mono.empty());

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("unknown")
                .password("password123")
                .build();

        StepVerifier.create(provider.authenticate(request))
                .expectError(InvalidGrantException.class)
                .verify();
    }

    @Test
    @DisplayName("Should lock account when failed attempts reach max limit")
    void shouldLockAccountWhenExceedingMaxAttempts() {
        User user = User.builder()
                .id("u-1")
                .username("john_doe")
                .passwordHash("hashed_pass")
                .status("ACTIVE")
                .failedAttempts(2) // Lần tiếp theo sẽ là 3 (bằng limit 3)
                .build();

        when(userRepository.findByUsername("john_doe")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("john_doe")
                .password("wrong_pass")
                .build();

        StepVerifier.create(provider.authenticate(request))
                .expectError(AccountLockedException.class)
                .verify();

        assertThat(user.getStatus()).isEqualTo("LOCKED");
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("Should reject authentication if account is currently locked")
    void shouldRejectWhenAccountIsLocked() {
        User user = User.builder()
                .id("u-1")
                .username("locked_user")
                .status("LOCKED")
                .lockedUntil(LocalDateTime.now().plusMinutes(10))
                .build();

        when(userRepository.findByUsername("locked_user")).thenReturn(Mono.just(user));

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("locked_user")
                .password("password123")
                .build();

        StepVerifier.create(provider.authenticate(request))
                .expectError(AccountLockedException.class)
                .verify();
    }
}
