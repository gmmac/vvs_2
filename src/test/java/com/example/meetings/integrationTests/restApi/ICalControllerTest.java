package com.example.meetings.integrationTests.restApi;

import com.example.meetings.controller.ICalController;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ICalControllerIntegrationTest.TestApplication.class)
class ICalControllerIntegrationTest {

        private final String EXAMPLE_USERNAME = "gustavo";
        private final String EXAMPLE_EMAIL = "gustavo@email.com";
        private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";
        private final String EXAMPLE_TOKEN = "ical-token-123";

        private final String EXAMPLE_ICAL_CONTENT = """
                        BEGIN:VCALENDAR
                        VERSION:2.0
                        BEGIN:VEVENT
                        SUMMARY:Meeting
                        END:VEVENT
                        END:VCALENDAR
                        """;

        @LocalServerPort
        private int port;

        @MockBean
        private UserRepository userRepository;

        @MockBean
        private MeetingService meetingService;

        @MockBean
        private ICalService icalService;

        @BeforeEach
        void setup() {
                RestAssured.port = port;
        }

        @AfterEach
        void reset() {
                RestAssured.reset();
        }

        /*
         * Tests endpoint GET /ical/{token}.ics
         * Condition: valid iCal token is provided
         */
        @Test
        void feed_validToken_returnsICalFeed() {
                User user = new User(
                                EXAMPLE_USERNAME,
                                EXAMPLE_EMAIL,
                                EXAMPLE_PASSWORD_HASH);

                List<Meeting> meetings = List.of();

                when(userRepository.findByIcalToken(EXAMPLE_TOKEN))
                                .thenReturn(Optional.of(user));
                when(meetingService.calendarFor(user))
                                .thenReturn(meetings);
                when(icalService.render(user, meetings))
                                .thenReturn(EXAMPLE_ICAL_CONTENT);

                given()
                                .when()
                                .get("/ical/" + EXAMPLE_TOKEN + ".ics")
                                .then()
                                .statusCode(200)
                                .header("Content-Type", containsString("text/calendar"))
                                .header("Content-Disposition", equalTo("inline; filename=\"meetings.ics\""))
                                .body(containsString("BEGIN:VCALENDAR"))
                                .body(containsString("BEGIN:VEVENT"))
                                .body(containsString("END:VCALENDAR"));

                verify(userRepository).findByIcalToken(EXAMPLE_TOKEN);
                verify(meetingService).calendarFor(user);
                verify(icalService).render(user, meetings);
        }

        /*
         * Tests endpoint GET /ical/{token}.ics
         * Condition: invalid iCal token is provided
         */
        @Test
        void feed_invalidToken_returnsNotFound() {
                when(userRepository.findByIcalToken(EXAMPLE_TOKEN))
                                .thenReturn(Optional.empty());

                given()
                                .when()
                                .get("/ical/" + EXAMPLE_TOKEN + ".ics")
                                .then()
                                .statusCode(404);

                verify(userRepository).findByIcalToken(EXAMPLE_TOKEN);
        }

        @SpringBootConfiguration
        @EnableAutoConfiguration(exclude = {
                        SecurityAutoConfiguration.class,
                        UserDetailsServiceAutoConfiguration.class
        })
        @Import(ICalController.class)
        static class TestApplication {
        }
}