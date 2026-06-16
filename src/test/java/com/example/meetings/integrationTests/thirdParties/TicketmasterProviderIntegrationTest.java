package com.example.meetings.integrationTests.thirdParties;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.TicketmasterProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class TicketmasterProviderIntegrationTest {

    private final String EXAMPLE_KEYWORD = "jazz";
    private final String UNKNOWN_KEYWORD = "unknown";
    private final String MISSING_KEYWORD = "missing";

    private final String EXAMPLE_API_KEY = "test-key";
    private final String EXAMPLE_COUNTRY_CODE = "PT";
    private final String EXAMPLE_SIZE = "20";

    private final String EXAMPLE_SOURCE = "Ticketmaster";
    private final String EXAMPLE_EXTERNAL_ID = "tm-1";
    private final String EXAMPLE_TITLE = "Classical Night";
    private final String EXAMPLE_DESCRIPTION = "Live concert";
    private final String EXAMPLE_URL = "https://example.com/event";
    private final String EXAMPLE_VENUE = "Altice Arena";
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
     * Tests integration with Ticketmaster API
     * Condition: external API returns valid and invalid events
     */
    @Test
    void search_validResponse_returnsOnlyValidDiscoveredEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(EXAMPLE_KEYWORD))
                .withQueryParam("size", equalTo(EXAMPLE_SIZE))
                .withQueryParam("apikey", equalTo(EXAMPLE_API_KEY))
                .withQueryParam("countryCode", equalTo(EXAMPLE_COUNTRY_CODE))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": [
                              {
                                "id": "tm-1",
                                "name": "Classical Night",
                                "info": "Live concert",
                                "url": "https://example.com/event",
                                "dates": {
                                  "start": {
                                    "dateTime": "2026-06-20T20:00:00Z"
                                  }
                                },
                                "_embedded": {
                                  "venues": [
                                    {
                                      "name": "Altice Arena"
                                    }
                                  ]
                                }
                              },
                              {
                                "id": "tm-2",
                                "name": "Event Without Date",
                                "info": "Missing start date",
                                "url": "https://example.com/no-date",
                                "dates": {
                                  "start": {}
                                }
                              },
                              {
                                "id": "tm-3",
                                "name": "Invalid Date Event",
                                "info": "Invalid date",
                                "url": "https://example.com/invalid-date",
                                "dates": {
                                  "start": {
                                    "dateTime": "invalid-date"
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """)));

        TicketmasterProvider provider = new TicketmasterProvider(
                EXAMPLE_API_KEY,
                EXAMPLE_COUNTRY_CODE,
                wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_KEYWORD);

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

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(EXAMPLE_KEYWORD))
                .withQueryParam("size", equalTo(EXAMPLE_SIZE))
                .withQueryParam("apikey", equalTo(EXAMPLE_API_KEY))
                .withQueryParam("countryCode", equalTo(EXAMPLE_COUNTRY_CODE)));
    }

    /*
     * Tests integration with Ticketmaster API
     * Condition: external API returns empty event list
     */
    @Test
    void search_emptyOrIncompleteResponse_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(UNKNOWN_KEYWORD))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": []
                          }
                        }
                        """)));

        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(MISSING_KEYWORD))
                .willReturn(okJson("""
                        {}
                        """)));

        TicketmasterProvider provider = new TicketmasterProvider(
                EXAMPLE_API_KEY,
                EXAMPLE_COUNTRY_CODE,
                wireMock.baseUrl());

        List<DiscoveredEvent> emptyResult = provider.search(UNKNOWN_KEYWORD);
        List<DiscoveredEvent> incompleteResult = provider.search(MISSING_KEYWORD);

        assertThat(emptyResult).isEmpty();
        assertThat(incompleteResult).isEmpty();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(UNKNOWN_KEYWORD)));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(MISSING_KEYWORD)));
    }

    /*
     * Tests integration with Ticketmaster API
     * Condition: external API returns server error
     */
    @Test
    void search_serverError_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(EXAMPLE_KEYWORD))
                .willReturn(serverError()));

        TicketmasterProvider provider = new TicketmasterProvider(
                EXAMPLE_API_KEY,
                EXAMPLE_COUNTRY_CODE,
                wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_KEYWORD);

        assertThat(result).isEmpty();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo(EXAMPLE_KEYWORD)));
    }

    /*
     * Tests integration with Ticketmaster API
     * Condition: provider is not configured with API key
     */
    @Test
    void search_missingApiKey_returnsEmptyListWithoutCallingApi() {
        TicketmasterProvider provider = new TicketmasterProvider(
                "",
                EXAMPLE_COUNTRY_CODE,
                wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_KEYWORD);

        assertThat(result).isEmpty();

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/events.json")));
    }
}
