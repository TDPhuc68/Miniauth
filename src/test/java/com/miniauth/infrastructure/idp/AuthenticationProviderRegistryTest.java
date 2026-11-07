package com.miniauth.infrastructure.idp;

import com.miniauth.core.exception.OAuth2Exception;
import com.miniauth.core.port.AuthenticationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationProviderRegistryTest {

    @Test
    @DisplayName("Should register and retrieve providers correctly")
    void shouldRegisterAndRetrieveProviders() {
        AuthenticationProvider localProvider = mock(AuthenticationProvider.class);
        when(localProvider.getProviderType()).thenReturn("LOCAL");

        AuthenticationProvider googleProvider = mock(AuthenticationProvider.class);
        when(googleProvider.getProviderType()).thenReturn("GOOGLE_MOCK");

        AuthenticationProviderRegistry registry = new AuthenticationProviderRegistry(List.of(localProvider, googleProvider));

        assertThat(registry.getSupportedProviders()).containsExactlyInAnyOrder("LOCAL", "GOOGLE_MOCK");
        assertThat(registry.getProvider("LOCAL")).isSameAs(localProvider);
        assertThat(registry.getProvider("local")).isSameAs(localProvider); // Case-insensitive
        assertThat(registry.getProvider("GOOGLE_MOCK")).isSameAs(googleProvider);
    }

    @Test
    @DisplayName("Should throw exception when provider is not supported")
    void shouldThrowExceptionWhenProviderNotSupported() {
        AuthenticationProvider localProvider = mock(AuthenticationProvider.class);
        when(localProvider.getProviderType()).thenReturn("LOCAL");

        AuthenticationProviderRegistry registry = new AuthenticationProviderRegistry(List.of(localProvider));

        assertThatThrownBy(() -> registry.getProvider("FACEBOOK"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("Unsupported identity provider");
    }
}
