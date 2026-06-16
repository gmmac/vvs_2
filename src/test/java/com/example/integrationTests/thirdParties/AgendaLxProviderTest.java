package com.example.integrationTests.thirdParties;

import com.example.meetings.discover.AgendaLxProvider;
import com.example.meetings.discover.DiscoveredEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class AgendaLxProviderIntegrationTest {

    private final String EXAMPLE_QUERY = "concert";
    private final String EMPTY_QUERY = "unknown";
    private final String EXAMPLE_PER_PAGE = "20";

    private final String EXAMPLE_SOURCE = "Agenda Cultural de Lisboa";
    private final String EXAMPLE_EXTERNAL_ID = "2001";
    private final String EXAMPLE_TITLE = "Concerto em Lisboa";
    private final String EXAMPLE_URL = "https://example.com/agendalx-event";
    private final String EXAMPLE_VENUE = "Teatro Lisboa";
    private final LocalDate EXAMPLE_DATE = LocalDate.parse("2099-06-20");
    private final LocalTime EXAMPLE_TIME = LocalTime.of(21, 30);
    private final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

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
     * Tests integration with AgendaLX API
     * Condition: external API returns valid and invalid events
     */
    @Test
    void search_validResponse_returnsOnlyValidDiscoveredEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo(EXAMPLE_QUERY))
                .withQueryParam("per_page", equalTo(EXAMPLE_PER_PAGE))
                .willReturn(okJson("""
                        [
                          {
                            "id": 2001,
                            "title": {
                              "rendered": "Concerto em Lisboa"
                            },
                            "description": [
                              "<p>Evento cultural ao vivo</p>"
                            ],
                            "occurences": [
                              "2099-06-20"
                            ],
                            "string_times": "qua: 21h30",
                            "link": "https://example.com/agendalx-event",
                            "venue": {
                              "1": {
                                "name": "Teatro Lisboa"
                              }
                            }
                          },
                          {
                            "id": 2002,
                            "title": {
                              "rendered": ""
                            },
                            "description": [
                              "<p>Evento sem título</p>"
                            ],
                            "occurences": [
                              "2099-06-20"
                            ],
                            "string_times": "qua: 21h30"
                          },
                          {
                            "id": 2003,
                            "title": {
                              "rendered": "Evento sem data"
                            },
                            "description": [
                              "<p>Evento sem ocorrência</p>"
                            ],
                            "occurences": [],
                            "string_times": "qua: 21h30"
                          }
                        ]
                        """)));

        AgendaLxProvider provider = new AgendaLxProvider(wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_QUERY);

        assertThat(result).hasSize(1);

        DiscoveredEvent event = result.get(0);

        assertThat(event.source()).isEqualTo(EXAMPLE_SOURCE);
        assertThat(event.externalId()).isEqualTo(EXAMPLE_EXTERNAL_ID);
        assertThat(event.title()).isEqualTo(EXAMPLE_TITLE);
        assertThat(event.description()).contains("Evento cultural ao vivo");
        assertThat(event.start()).isEqualTo(EXAMPLE_DATE.atTime(EXAMPLE_TIME).atZone(LISBON).toInstant());
        assertThat(event.end()).isNull();
        assertThat(event.url()).isEqualTo(EXAMPLE_URL);
        assertThat(event.venue()).isEqualTo(EXAMPLE_VENUE);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo(EXAMPLE_QUERY))
                .withQueryParam("per_page", equalTo(EXAMPLE_PER_PAGE)));
    }

    /*
     * Tests integration with AgendaLX API
     * Condition: external API returns no usable events
     */
    @Test
    void search_emptyOrIncompleteResponse_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo(EMPTY_QUERY))
                .willReturn(okJson("""
                        []
                        """)));

        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("past"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 2004,
                            "title": {
                              "rendered": "Evento antigo"
                            },
                            "occurences": [
                              "2000-01-01"
                            ],
                            "string_times": "qua: 21h30"
                          }
                        ]
                        """)));

        AgendaLxProvider provider = new AgendaLxProvider(wireMock.baseUrl());

        List<DiscoveredEvent> emptyResult = provider.search(EMPTY_QUERY);
        List<DiscoveredEvent> pastResult = provider.search("past");

        assertThat(emptyResult).isEmpty();
        assertThat(pastResult).isEmpty();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo(EMPTY_QUERY)));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("past")));
    }

    /*
     * Tests integration with AgendaLX API
     * Condition: external API returns event without parseable time
     */
    @Test
    void search_eventWithoutParseableTime_usesFallbackTime() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("fallback"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 2005,
                            "title": {
                              "rendered": "Evento com hora por defeito"
                            },
                            "description": [
                              "<p>Sem hora estruturada</p>"
                            ],
                            "occurences": [
                              "2099-06-20"
                            ],
                            "string_times": "hora a definir",
                            "link": "https://example.com/fallback",
                            "venue": {
                              "1": {
                                "name": "Auditório Lisboa"
                              }
                            }
                          }
                        ]
                        """)));

        AgendaLxProvider provider = new AgendaLxProvider(wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search("fallback");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).start())
                .isEqualTo(EXAMPLE_DATE.atTime(LocalTime.of(20, 0)).atZone(LISBON).toInstant());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("fallback")));
    }

    /*
     * Tests integration with AgendaLX API
     * Condition: external API returns server error
     */
    @Test
    void search_serverError_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo(EXAMPLE_QUERY))
                .willReturn(serverError()));

        AgendaLxProvider provider = new AgendaLxProvider(wireMock.baseUrl());

        List<DiscoveredEvent> result = provider.search(EXAMPLE_QUERY);

        assertThat(result).isEmpty();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo(EXAMPLE_QUERY)));
    }
}