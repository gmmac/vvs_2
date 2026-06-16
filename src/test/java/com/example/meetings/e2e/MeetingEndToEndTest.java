package com.example.meetings.e2e;

import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeetingEndToEndTest {

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository meetingParticipantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String RAW_PASSWORD = "password123";

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp() {
        meetingParticipantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    /*
     * E2E workflow 1:
     * Login and access calendar.
     */
    @Test
    void login_validCredentials_redirectsToCalendar() {
        createUser("gustavo", "gustavo@email.com");

        login("gustavo", RAW_PASSWORD);

        WebElement calendarPage = wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId("calendar-page")));

        assertThat(calendarPage.isDisplayed()).isTrue();
    }

    /*
     * E2E workflow 2:
     * Create meeting and check if it appears in the calendar.
     */
    @Test
    void createMeeting_validData_meetingAppearsInCalendar() {
        createUser("gustavo", "gustavo@email.com");

        login("gustavo", RAW_PASSWORD);

        createMeetingThroughUi(
                "Project Meeting",
                "Discuss project progress",
                "2026-06-20T10:00",
                "2026-06-20T11:00",
                null);

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Project Meeting')]")));

        assertThat(driver.getPageSource()).contains("Project Meeting");
    }

    /*
     * E2E workflow 3:
     * Organizer creates a meeting with an invitee.
     * Invitee accepts the invitation.
     * Meeting appears in invitee calendar.
     */
    @Test
    void createMeetingWithInvitee_inviteeAccepts_meetingAppearsInInviteeCalendar() {
        createUser("organizer", "organizer@email.com");
        createUser("invitee", "invitee@email.com");

        login("organizer", RAW_PASSWORD);

        createMeetingThroughUi(
                "Team Sync",
                "Weekly synchronization meeting",
                "2026-06-21T10:00",
                "2026-06-21T11:00",
                "invitee");

        logout();

        login("invitee", RAW_PASSWORD);

        clickByTestId("invitations-link");

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Team Sync')]")));

        clickByTestId("accept-invite");

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId("calendar-page")));

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Team Sync')]")));

        assertThat(driver.getPageSource()).contains("Team Sync");
    }

    /*
     * E2E workflow 4:
     * User creates a meeting and then tries to create another one
     * with overlapping time.
     */
    @Test
    void createMeetingWithOverlappingTime_showsConflictError() {
        createUser("gustavo", "gustavo@email.com");

        login("gustavo", RAW_PASSWORD);

        createMeetingThroughUi(
                "First Meeting",
                "Initial meeting",
                "2026-06-22T10:00",
                "2026-06-22T11:00",
                null);

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'First Meeting')]")));

        createMeetingThroughUi(
                "Conflicting Meeting",
                "This meeting overlaps with another one",
                "2026-06-22T10:30",
                "2026-06-22T11:30",
                null);

        WebElement errorMessage = wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId("error-message")));

        assertThat(errorMessage.getText().toLowerCase())
                .containsAnyOf("conflict", "overlap", "sobreposição", "conflito");

        assertThat(driver.getPageSource()).doesNotContain("Conflicting Meeting");
    }

    private User createUser(String username, String email) {
        User user = new User(
                username,
                email,
                passwordEncoder.encode(RAW_PASSWORD));

        return userRepository.saveAndFlush(user);
    }

    private void login(String username, String password) {
        driver.get(baseUrl() + "/login");

        typeByTestId("login-username", username);
        typeByTestId("login-password", password);

        clickByTestId("login-submit");

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId("calendar-page")));
    }

    private void logout() {
        clickByTestId("logout-button");

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId("login-username")));
    }

    private void createMeetingThroughUi(
            String title,
            String description,
            String start,
            String end,
            String participantUsername) {

        clickByTestId("create-meeting-link");

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId("meeting-title")));

        typeByTestId("meeting-title", title);
        typeByTestId("meeting-description", description);
        typeByTestId("meeting-start", start);
        typeByTestId("meeting-end", end);

        if (participantUsername != null && !participantUsername.isBlank()) {
            typeByTestId("meeting-participants", participantUsername);
        }

        clickByTestId("meeting-submit");
    }

    private void typeByTestId(String testId, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(
                byTestId(testId)));

        element.clear();
        element.sendKeys(value);
    }

    private void clickByTestId(String testId) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(
                byTestId(testId)));

        element.click();
    }

    private By byTestId(String testId) {
        return By.cssSelector("[data-testid='" + testId + "']");
    }
}