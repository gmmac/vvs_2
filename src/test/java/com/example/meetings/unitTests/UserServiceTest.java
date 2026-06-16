package com.example.meetings.unitTests;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private final String EXAMPLE_USERNAME = "gustavo";
    private final String EXAMPLE_EMAIL = "gustavo@email.com";
    private final String EXAMPLE_PASSWORD_RAW = "pswd";
    private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    /*
        Tests method register
        Condition: username is available
    */
    @Test
    void register_availableUsername_createsUser() {
        when(userRepository.existsByUsername(EXAMPLE_USERNAME)).thenReturn(false);
        when(passwordEncoder.encode(EXAMPLE_PASSWORD_RAW)).thenReturn(EXAMPLE_PASSWORD_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_RAW);

        assertThat(user.getUsername()).isEqualTo(EXAMPLE_USERNAME);
        assertThat(user.getEmail()).isEqualTo(EXAMPLE_EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo(EXAMPLE_PASSWORD_HASH);
        assertThat(user.getIcalToken()).isNotBlank();

        verify(userRepository).existsByUsername(EXAMPLE_USERNAME);
        verify(passwordEncoder).encode(EXAMPLE_PASSWORD_RAW);
        verify(userRepository).save(any(User.class));
    }

    /*
        Tests method register
        Condition: username already exists
    */
    @Test
    void register_existingUsername_throwsException() {
        when(userRepository.existsByUsername(EXAMPLE_USERNAME)).thenReturn(true);

        assertThatThrownBy(() ->
                userService.register(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_RAW)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already taken");

        verify(userRepository).existsByUsername(EXAMPLE_USERNAME);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    /*
        Tests method requireByUsername
        Condition: user exists
    */
    @Test
    void requireByUsername_existingUser_returnsUser() {
        User user = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

        when(userRepository.findByUsername(EXAMPLE_USERNAME)).thenReturn(Optional.of(user));

        User result = userService.requireByUsername(EXAMPLE_USERNAME);

        assertThat(result).isSameAs(user);

        verify(userRepository).findByUsername(EXAMPLE_USERNAME);
    }

    /*
        Tests method requireByUsername
        Condition: user does not exist
    */
    @Test
    void requireByUsername_missingUser_throwsException() {
        String unknownUsername = "unknown";

        when(userRepository.findByUsername(unknownUsername)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requireByUsername(unknownUsername))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown user: unknown");

        verify(userRepository).findByUsername(unknownUsername);
    }
}