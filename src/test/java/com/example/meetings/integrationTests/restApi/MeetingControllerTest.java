package com.example.meetings.integrationTests.restApi;

import com.example.meetings.controller.MeetingController;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MeetingControllerIntegrationTest.TestApplication.class)
class MeetingControllerIntegrationTest {

    private static final String EXAMPLE_USERNAME = "gustavo";
    private static final String EXAMPLE_PASSWORD = "pswd";

    private final String EXAMPLE_EMAIL = "gustavo@email.com";
    private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";

    private final String EXAMPLE_TITLE = "Project Meeting";
    private final String EXAMPLE_DESCRIPTION = "Discuss project progress";
    private final String EXAMPLE_START = "2026-06-20T10:00";
    private final String EXAMPLE_END = "2026-06-20T11:00";
    private final String EXAMPLE_INVITEES = "ana bruno";
    private final String EXAMPLE_ERROR_MESSAGE = "End must be after start";
    private final Long EXAMPLE_MEETING_ID = 1L;

    @LocalServerPort
    private int port;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

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
     * Tests endpoint GET /meetings/new
     * Condition: meeting proposal page is requested
     */
    @Test
    void proposeForm_getMeetingProposalPage_returnsProposePage() {
        given()
                .when()
                .get("/meetings/new")
                .then()
                .statusCode(200);
    }

    /*
     * Tests endpoint POST /meetings/new
     * Condition: valid meeting proposal data is submitted
     */
    @Test
    @SuppressWarnings("unchecked")
    void propose_validData_redirectsToCalendarAndCreatesMeeting() {
        User organizer = new User(
                EXAMPLE_USERNAME,
                EXAMPLE_EMAIL,
                EXAMPLE_PASSWORD_HASH);

        when(userService.requireByUsername(EXAMPLE_USERNAME)).thenReturn(organizer);

        given()
                .redirects().follow(false)
                .contentType(ContentType.URLENC)
                .formParam("title", EXAMPLE_TITLE)
                .formParam("description", EXAMPLE_DESCRIPTION)
                .formParam("start", EXAMPLE_START)
                .formParam("end", EXAMPLE_END)
                .formParam("invitees", EXAMPLE_INVITEES)
                .when()
                .post("/meetings/new")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/calendar"));

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<List<String>> inviteesCaptor = ArgumentCaptor.forClass(List.class);

        verify(userService).requireByUsername(EXAMPLE_USERNAME);
        verify(meetingService).propose(
                eq(organizer),
                eq(EXAMPLE_TITLE),
                eq(EXAMPLE_DESCRIPTION),
                startCaptor.capture(),
                endCaptor.capture(),
                inviteesCaptor.capture());

        ZoneId zone = ZoneId.systemDefault();

        assertThat(startCaptor.getValue())
                .isEqualTo(LocalDateTime.parse(EXAMPLE_START).atZone(zone).toInstant());

        assertThat(endCaptor.getValue())
                .isEqualTo(LocalDateTime.parse(EXAMPLE_END).atZone(zone).toInstant());

        assertThat(inviteesCaptor.getValue())
                .containsExactly("ana", "bruno");
    }

    /*
     * Tests endpoint POST /meetings/new
     * Condition: meeting service throws validation exception
     */
    @Test
    void propose_invalidData_returnsProposePageWithError() {
        User organizer = new User(
                EXAMPLE_USERNAME,
                EXAMPLE_EMAIL,
                EXAMPLE_PASSWORD_HASH);

        when(userService.requireByUsername(EXAMPLE_USERNAME)).thenReturn(organizer);

        doThrow(new IllegalArgumentException(EXAMPLE_ERROR_MESSAGE))
                .when(meetingService)
                .propose(
                        eq(organizer),
                        eq(EXAMPLE_TITLE),
                        eq(EXAMPLE_DESCRIPTION),
                        any(Instant.class),
                        any(Instant.class),
                        any(List.class));

        given()
                .contentType(ContentType.URLENC)
                .formParam("title", EXAMPLE_TITLE)
                .formParam("description", EXAMPLE_DESCRIPTION)
                .formParam("start", EXAMPLE_START)
                .formParam("end", EXAMPLE_END)
                .formParam("invitees", EXAMPLE_INVITEES)
                .when()
                .post("/meetings/new")
                .then()
                .statusCode(200)
                .body(containsString(EXAMPLE_ERROR_MESSAGE))
                .body(containsString(EXAMPLE_TITLE))
                .body(containsString(EXAMPLE_DESCRIPTION));

        verify(userService).requireByUsername(EXAMPLE_USERNAME);
    }

    /*
     * Tests endpoint POST /meetings/{id}/respond
     * Condition: authenticated user accepts meeting invite
     */
    @Test
    void respond_acceptInvite_redirectsToCalendarAndAcceptsInvite() {
        User user = new User(
                EXAMPLE_USERNAME,
                EXAMPLE_EMAIL,
                EXAMPLE_PASSWORD_HASH);

        when(userService.requireByUsername(EXAMPLE_USERNAME)).thenReturn(user);

        given()
                .redirects().follow(false)
                .contentType(ContentType.URLENC)
                .formParam("action", "accept")
                .when()
                .post("/meetings/" + EXAMPLE_MEETING_ID + "/respond")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/calendar"));

        verify(userService).requireByUsername(EXAMPLE_USERNAME);
        verify(meetingService).respond(
                EXAMPLE_MEETING_ID,
                user,
                InviteStatus.ACCEPTED);
    }

    /*
     * Tests endpoint POST /meetings/{id}/respond
     * Condition: authenticated user declines meeting invite
     */
    @Test
    void respond_declineInvite_redirectsToCalendarAndDeclinesInvite() {
        User user = new User(
                EXAMPLE_USERNAME,
                EXAMPLE_EMAIL,
                EXAMPLE_PASSWORD_HASH);

        when(userService.requireByUsername(EXAMPLE_USERNAME)).thenReturn(user);

        given()
                .redirects().follow(false)
                .contentType(ContentType.URLENC)
                .formParam("action", "decline")
                .when()
                .post("/meetings/" + EXAMPLE_MEETING_ID + "/respond")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/calendar"));

        verify(userService).requireByUsername(EXAMPLE_USERNAME);
        verify(meetingService).respond(
                EXAMPLE_MEETING_ID,
                user,
                InviteStatus.DECLINED);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import(MeetingController.class)
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
