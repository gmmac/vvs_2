package com.example.meetings.integrationTests.restApi;

import com.example.meetings.controller.DiscoveryController;
import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
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
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = DiscoveryControllerIntegrationTest.TestApplication.class)
class DiscoveryControllerIntegrationTest {

    private static final String EXAMPLE_USERNAME = "gustavo";
    private static final String EXAMPLE_PASSWORD = "pswd";

    private final String EXAMPLE_EMAIL = "gustavo@email.com";
    private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";

    private final String EXAMPLE_QUERY = "cinema";
    private final String EXAMPLE_SOURCE = "Ticketmaster";
    private final String EXAMPLE_EXTERNAL_ID = "tm-1";
    private final String EXAMPLE_TITLE = "Cinema Night";
    private final String EXAMPLE_DESCRIPTION = "Cinema Night Event.";
    private final String EXAMPLE_START = "2026-06-20T20:00:00Z";
    private final String EXAMPLE_END = "2026-06-20T22:00:00Z";
    private final String EXAMPLE_URL = "https://example.com/event";
    private final String EXAMPLE_VENUE = "Bairro Alto";

    @LocalServerPort
    private int port;

    @MockBean
    private DiscoveryService discoveryService;

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
     * Tests endpoint GET /discover
     * Condition: discover page is requested without query
     */
    @Test
    void discover_withoutQuery_returnsDiscoverPageWithoutSearching() {
        EventProvider provider = mock(EventProvider.class);

        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));

        given()
                .when()
                .get("/discover")
                .then()
                .statusCode(200)
                .body(containsString("discover"));

        verify(discoveryService).providers();
        verify(discoveryService, never()).search(anyString());
    }


    @Test
    void discover_withQueryAndConfiguredProvider_returnsSearchResults() {
        EventProvider provider = mock(EventProvider.class);

        DiscoveredEvent event = new DiscoveredEvent(
                EXAMPLE_SOURCE,
                EXAMPLE_EXTERNAL_ID,
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                Instant.parse(EXAMPLE_START),
                null,
                EXAMPLE_URL,
                EXAMPLE_VENUE);

        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));
        when(discoveryService.search(EXAMPLE_QUERY)).thenReturn(List.of(event));

        given()
                .queryParam("q", EXAMPLE_QUERY)
                .when()
                .get("/discover")
                .then()
                .statusCode(200)
                .body(containsString(EXAMPLE_QUERY))
                .body(containsString(EXAMPLE_TITLE));

        verify(discoveryService).providers();
        verify(discoveryService).search(EXAMPLE_QUERY);
    }

    /*
     * Tests endpoint POST /discover/copy
     * Condition: authenticated user copies discovered event to calendar
     */
    @Test
    void copy_validDiscoveredEvent_redirectsToCalendarAndCopiesEvent() {
        User user = new User(
                EXAMPLE_USERNAME,
                EXAMPLE_EMAIL,
                EXAMPLE_PASSWORD_HASH);

        when(userService.requireByUsername(EXAMPLE_USERNAME)).thenReturn(user);

        given()
                .redirects().follow(false)
                .contentType(ContentType.URLENC)
                .formParam("source", EXAMPLE_SOURCE)
                .formParam("externalId", EXAMPLE_EXTERNAL_ID)
                .formParam("title", EXAMPLE_TITLE)
                .formParam("description", EXAMPLE_DESCRIPTION)
                .formParam("start", EXAMPLE_START)
                .formParam("end", EXAMPLE_END)
                .formParam("url", EXAMPLE_URL)
                .formParam("venue", EXAMPLE_VENUE)
                .when()
                .post("/discover/copy")
                .then()
                .statusCode(302)
                .header("Location", endsWith("/calendar"));

        ArgumentCaptor<DiscoveredEvent> eventCaptor = ArgumentCaptor.forClass(DiscoveredEvent.class);

        verify(userService).requireByUsername(EXAMPLE_USERNAME);
        verify(meetingService).copyFromDiscovered(eq(user), eventCaptor.capture());

        DiscoveredEvent copiedEvent = eventCaptor.getValue();

        assertThat(copiedEvent.source()).isEqualTo(EXAMPLE_SOURCE);
        assertThat(copiedEvent.externalId()).isEqualTo(EXAMPLE_EXTERNAL_ID);
        assertThat(copiedEvent.title()).isEqualTo(EXAMPLE_TITLE);
        assertThat(copiedEvent.description()).isEqualTo(EXAMPLE_DESCRIPTION);
        assertThat(copiedEvent.start()).isEqualTo(Instant.parse(EXAMPLE_START));
        assertThat(copiedEvent.end()).isEqualTo(Instant.parse(EXAMPLE_END));
        assertThat(copiedEvent.url()).isEqualTo(EXAMPLE_URL);
        assertThat(copiedEvent.venue()).isEqualTo(EXAMPLE_VENUE);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import(DiscoveryController.class)
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