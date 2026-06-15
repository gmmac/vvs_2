package com.example.meetings.service.unitTests;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.AppUserDetailsService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    private final String EXAMPLE_USERNAME = "gustavo";
    private final String EXAMPLE_EMAIL = "gustavo@email.com";
    private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    /*
        Tests method loadUserByUsername
        Condition: user exists
    */
    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        User user = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

        when(userRepository.findByUsername(EXAMPLE_USERNAME)).thenReturn(Optional.of(user));

        UserDetails result = appUserDetailsService.loadUserByUsername(EXAMPLE_USERNAME);

        assertThat(result.getUsername()).isEqualTo(EXAMPLE_USERNAME);
        assertThat(result.getPassword()).isEqualTo(EXAMPLE_PASSWORD_HASH);
        assertThat(result.getAuthorities())
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER"));

        verify(userRepository).findByUsername(EXAMPLE_USERNAME);
    }

    /*
        Tests method loadUserByUsername
        Condition: user does not exist
    */
    @Test
    void loadUserByUsername_missingUser_throwsException() {
        String unknownUsername = "unknown";

        when(userRepository.findByUsername(unknownUsername)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername(unknownUsername))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Unknown user: unknown");

        verify(userRepository).findByUsername(unknownUsername);
    }
}