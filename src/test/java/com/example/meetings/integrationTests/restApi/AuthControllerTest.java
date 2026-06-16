package com.example.meetings.integrationTests.restApi;

import com.example.meetings.controller.AuthController;
import com.example.meetings.service.UserService;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = AuthControllerIntegrationTest.TestApplication.class
)
class AuthControllerIntegrationTest {

        private final String EXAMPLE_USERNAME = "gustavo";
        private final String EXAMPLE_EMAIL = "gustavo@email.com";
        private final String EXAMPLE_PASSWORD = "pswd";
        private final String EXAMPLE_ERROR_MESSAGE = "Username already exists";

        @LocalServerPort
        private int port;

        @MockBean
        private UserService userService;

        @BeforeEach
        void setup() {
                RestAssured.port = port;
        }

        @AfterEach
        void reset() {
                RestAssured.reset();
        }

        /*
         * Tests endpoint GET /login
         * Condition: login page is requested
         */
        @Test
        void login_getLoginPage_returnsLoginPage() {
                given()
                .when()
                        .get("/login")
                .then()
                        .statusCode(200)
                        .body(containsString("login"));
        }

        /*
         * Tests endpoint GET /register
         * Condition: register page is requested
         */
        @Test
        void registerForm_getRegisterPage_returnsRegisterPage() {
                given()
                .when()
                        .get("/register")
                .then()
                        .statusCode(200)
                        .body(containsString("register"));
        }

        /*
         * Tests endpoint POST /register
         * Condition: valid registration data is submitted
         */
        @Test
        void register_validData_redirectsToLogin() {
                given()
                        .redirects().follow(false)
                        .contentType(ContentType.URLENC)
                        .formParam("username", EXAMPLE_USERNAME)
                        .formParam("email", EXAMPLE_EMAIL)
                        .formParam("password", EXAMPLE_PASSWORD)
                .when()
                        .post("/register")
                .then()
                        .statusCode(302)
                        .header("Location", endsWith("/login?registered"));

                verify(userService).register(
                        EXAMPLE_USERNAME,
                        EXAMPLE_EMAIL,
                        EXAMPLE_PASSWORD);
        }

        /*
         * Tests endpoint POST /register
         * Condition: user service throws validation exception
         */
        @Test
        void register_invalidData_returnsRegisterPageWithError() {
                doThrow(new IllegalArgumentException(EXAMPLE_ERROR_MESSAGE))
                        .when(userService)
                        .register(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD);

                given()
                        .contentType(ContentType.URLENC)
                        .formParam("username", EXAMPLE_USERNAME)
                        .formParam("email", EXAMPLE_EMAIL)
                        .formParam("password", EXAMPLE_PASSWORD)
                .when()
                        .post("/register")
                .then()
                        .statusCode(200)
                        .body(containsString(EXAMPLE_ERROR_MESSAGE))
                        .body(containsString(EXAMPLE_USERNAME))
                        .body(containsString(EXAMPLE_EMAIL));

                verify(userService).register(
                        EXAMPLE_USERNAME,
                        EXAMPLE_EMAIL,
                        EXAMPLE_PASSWORD);
        }

        /*
         * Tests endpoint GET /
         * Condition: root page is requested
         */
        @Test
        void root_getRoot_redirectsToCalendar() {
                given()
                        .redirects().follow(false)
                .when()
                        .get("/")
                .then()
                        .statusCode(302)
                        .header("Location", endsWith("/calendar"));
        }

        @SpringBootConfiguration
        @EnableAutoConfiguration(exclude = {
                SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        })
        @Import(AuthController.class)
        static class TestApplication {
        }
}