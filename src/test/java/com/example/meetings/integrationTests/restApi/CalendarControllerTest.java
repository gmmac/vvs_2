package com.example.meetings.integrationTests.restApi;

import com.example.meetings.controller.CalendarController;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import io.restassured.RestAssured;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = CalendarControllerIntegrationTest.TestApplication.class, properties = "app.base-url=http://localhost")
class CalendarControllerIntegrationTest {

    private static final String EXAMPLE_USERNAME = "gustavo";
    private static final String EXAMPLE_PASSWORD = "pswd";

    private final String EXAMPLE_EMAIL = "gustavo@email.com";
    private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";

    @LocalServerPort
    private int port;

    @MockBean
    private UserService userService;

    @MockBean
    private MeetingService meetingService;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
    }

    @AfterEach
    void reset() {
        RestAssured.reset();
        SecurityContextHolder.clearContext();
    }

    /*
     * Tests endpoint GET /calendar
     * Condition: authenticated user requests calendar page
     */
    @Test
    void calendar_authenticatedUser_returnsCalendarPage() {
        User user = new User(
                EXAMPLE_USERNAME,
                EXAMPLE_EMAIL,
                EXAMPLE_PASSWORD_HASH);

        String icalToken = user.getIcalToken();

        when(userService.requireByUsername(EXAMPLE_USERNAME)).thenReturn(user);
        when(meetingService.calendarFor(user)).thenReturn(List.of());
        when(meetingService.pendingInvitesFor(user)).thenReturn(List.of());

        given()
                .when()
                .get("/calendar")
                .then()
                .statusCode(200)
                .body(containsString(icalToken))
                .body(containsString("http://localhost/ical/" + icalToken + ".ics"))
                .body(containsString("webcal://localhost/ical/" + icalToken + ".ics"));

        verify(userService).requireByUsername(EXAMPLE_USERNAME);
        verify(meetingService).calendarFor(user);
        verify(meetingService).pendingInvitesFor(user);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import(CalendarController.class)
    static class TestApplication {

        @Bean
        WebMvcConfigurer authenticationPrincipalConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new AuthenticationPrincipalArgumentResolver());
                }
            };
        }

        @Bean
        Filter testAuthenticationFilter() {
            return new Filter() {
                @Override
                public void doFilter(ServletRequest request,
                        ServletResponse response,
                        FilterChain chain)
                        throws java.io.IOException, jakarta.servlet.ServletException {

                    UserDetails principal = org.springframework.security.core.userdetails.User
                            .withUsername(EXAMPLE_USERNAME)
                            .password(EXAMPLE_PASSWORD)
                            .roles("USER")
                            .build();

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            principal.getPassword(),
                            principal.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    chain.doFilter(request, response);
                }
            };
        }
    }
}