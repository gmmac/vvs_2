package com.example.meetings.integrationTests.db;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryDatabaseIntegrationTest {

    private final String USERNAME = "gustavo";
    private final String EMAIL = "gustavo@email.com";
    private final String PASSWORD_HASH = "hash_pswd";

    private final String OTHER_EMAIL = "macedo@email.com";
    private final String OTHER_PASSWORD_HASH = "hash_pswd";

    @Autowired
    private UserRepository userRepository;

    /*
     * Tests method findByUsername
     * Condition: user exists with the given username
     */
    @Test
    void findByUsername_existingUsername_returnsUser() {
        User user = userRepository.save(new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH));

        Optional<User> result = userRepository.findByUsername(USERNAME);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(user.getId());
        assertThat(result.get().getUsername()).isEqualTo(USERNAME);
        assertThat(result.get().getEmail()).isEqualTo(EMAIL);
    }

    /*
     * Tests method findByUsername
     * Condition: no user exists with the given username
     */
    @Test
    void findByUsername_nonExistingUsername_returnsEmptyOptional() {
        userRepository.save(new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH));

        Optional<User> result = userRepository.findByUsername("not_existing_user");

        assertThat(result).isEmpty();
    }

    /*
     * Tests method existsByUsername
     * Condition: user exists with the given username
     */
    @Test
    void existsByUsername_existingUsername_returnsTrue() {
        userRepository.save(new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH));

        boolean result = userRepository.existsByUsername(USERNAME);

        assertThat(result).isTrue();
    }

    /*
     * Tests method existsByUsername
     * Condition: no user exists with the given username
     */
    @Test
    void existsByUsername_nonExistingUsername_returnsFalse() {
        userRepository.save(new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH));

        boolean result = userRepository.existsByUsername("not_existing_user");

        assertThat(result).isFalse();
    }

    /*
     * Tests method findByIcalToken
     * Condition: user exists with the given iCal token
     */
    @Test
    void findByIcalToken_existingToken_returnsUser() {
        User user = new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH);

        String token = user.getIcalToken();

        User savedUser = userRepository.save(user);

        Optional<User> result = userRepository.findByIcalToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(savedUser.getId());
        assertThat(result.get().getUsername()).isEqualTo(USERNAME);
        assertThat(result.get().getIcalToken()).isEqualTo(token);
    }

    /*
     * Tests method findByIcalToken
     * Condition: no user exists with the given iCal token
     */
    @Test
    void findByIcalToken_nonExistingToken_returnsEmptyOptional() {
        userRepository.save(new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH));

        Optional<User> result = userRepository.findByIcalToken("invalid-token");

        assertThat(result).isEmpty();
    }

    /*
     * Tests unique constraint on username
     * Condition: two users are saved with the same username
     */
    @Test
    void save_duplicateUsername_throwsException() {
        userRepository.saveAndFlush(new User(
                USERNAME,
                EMAIL,
                PASSWORD_HASH));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User(
                USERNAME,
                OTHER_EMAIL,
                OTHER_PASSWORD_HASH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}