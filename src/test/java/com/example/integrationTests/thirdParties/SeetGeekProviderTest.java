package com.example.integrationTests.thirdParties;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.SeatGeekProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class SeatGeekProviderIntegrationTest {

    private final String EXAMPLE_QUERY = "football";
    private final String EMPTY_QUERY = "unknown";
    private final String EXAMPLE_CLIENT_ID = "test-client-id";
    private final String EXAMPLE_PER_PAGE = "20";

    private final String EXAMPLE_SOURCE = "SeatGeek";
    private final String EXAMPLE_EXTERNAL_ID = "1001";
    private final String EXAMPLE_TITLE = "Benfica x Sporting";
    private final String EXAMPLE_DESCRIPTION = "Championship game";
    private final String EXAMPLE_URL = "https://example.com/seatgeek-event";
    private final String EXAMPLE_VENUE = "Estádio da Luz";
    private final Instant EXAMPLE_START = Instant.parse("2026-06-20T20:00:00Z");

    private WireMockServer wireMock;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void reset() {
        wireMock.stop();
    }

    /*
     * Tests integration with SeatGeek API
     * Condition: external API returns valid and invalid events
     */
    @Test
    void search_validResponse_returnsOnlyValidDiscoveredEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo(EXAMPLE_QUERY))
                .withQueryParam("per_page", equalTo(EXAMPLE_PER_PAGE))
                .withQueryParam("client_id", equalTo(EXAMPLE_CLIENT_ID))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 1001,
                              "title": "Benfica x Sporting",
                              "short_title": "Football",
                              "description": "Championship game",
                              "datetime_utc": "2026-06-20T20:00:00",
                              "url": "https://example.com/seatgeek-event",
                              "venue": {
                                "name": "Estádio da Luz"
                              }
                            },
                            {
                              "id": 1002,
                              "title": "Event Without Date",
                              "description": "Missing date",
                              "url": "https://example.com/no-date",
                              "venue": {
                                "name": "Unknown Venue"
                              }
                            },
                            {
                              "id": 1003,
                              "title": "Invalid Date Event",
                              "description": "Invalid date",
                              "datetime_utc": "invalid-date",
                              "url": "https://example.com/invalid-date"
                            }
                          ]
                        }
                        """)));

        SeatGeekProvider provider = new SeatGeekProvider(
                EXAMPLE_CLIENT_ID,
                wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_QUERY);

        assertThat(result).hasSize(1);

        DiscoveredEvent event = result.get(0);

        assertThat(event.source()).isEqualTo(EXAMPLE_SOURCE);
        assertThat(event.externalId()).isEqualTo(EXAMPLE_EXTERNAL_ID);
        assertThat(event.title()).isEqualTo(EXAMPLE_TITLE);
        assertThat(event.description()).isEqualTo(EXAMPLE_DESCRIPTION);
        assertThat(event.start()).isEqualTo(EXAMPLE_START);
        assertThat(event.end()).isNull();
        assertThat(event.url()).isEqualTo(EXAMPLE_URL);
        assertThat(event.venue()).isEqualTo(EXAMPLE_VENUE);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo(EXAMPLE_QUERY))
                .withQueryParam("per_page", equalTo(EXAMPLE_PER_PAGE))
                .withQueryParam("client_id", equalTo(EXAMPLE_CLIENT_ID)));
    }

    /*
     * Tests integration with SeatGeek API
     * Condition: external API returns no usable events
     */
    @Test
    void search_emptyOrIncompleteResponse_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo(EMPTY_QUERY))
                .willReturn(okJson("""
                        {
                          "events": []
                        }
                        """)));

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("missing"))
                .willReturn(okJson("""
                        {}
                        """)));

        SeatGeekProvider provider = new SeatGeekProvider(
                EXAMPLE_CLIENT_ID,
                wireMock.baseUrl());

        List<DiscoveredEvent> emptyResult = provider.search(EMPTY_QUERY);
        List<DiscoveredEvent> incompleteResult = provider.search("missing");

        assertThat(emptyResult).isEmpty();
        assertThat(incompleteResult).isEmpty();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo(EMPTY_QUERY)));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("missing")));
    }

    /*
     * Tests integration with SeatGeek API
     * Condition: external API returns server error
     */
    @Test
    void search_serverError_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo(EXAMPLE_QUERY))
                .willReturn(serverError()));

        SeatGeekProvider provider = new SeatGeekProvider(
                EXAMPLE_CLIENT_ID,
                wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_QUERY);

        assertThat(result).isEmpty();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo(EXAMPLE_QUERY)));
    }

    /*
     * Tests integration with SeatGeek API
     * Condition: provider is not configured with client id
     */
    @Test
    void search_missingClientId_returnsEmptyListWithoutCallingApi() {
        SeatGeekProvider provider = new SeatGeekProvider(
                "",
                wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_QUERY);

        assertThat(result).isEmpty();

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/events")));
    }
}