package com.example.meetings.e2e;

import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeetingEndToEndTest {

    /*
     * Mock external discovery service.
     * These E2E tests focus on authentication, calendar,
     * meeting creation, invitations and scheduling conflicts.
     */
    @MockBean
    private DiscoveryService discoveryService;

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

        WebDriverManager.chromedriver()
                .browserVersion("148")
                .setup();

        ChromeOptions options = new ChromeOptions();
        options.setBinary("/usr/sbin/chromium");
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

        wait.until(ExpectedConditions.urlContains("/calendar"));

        assertThat(driver.getPageSource()).contains("Your calendar");
        assertThat(driver.getPageSource()).contains("Signed in as");
        assertThat(driver.getPageSource()).contains("gustavo");
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
                null
        );

        wait.until(ExpectedConditions.urlContains("/calendar"));

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Project Meeting')]")));

        assertThat(driver.getPageSource()).contains("Project Meeting");
        assertThat(driver.getPageSource()).contains("Discuss project progress");
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
                "invitee"
        );

        wait.until(ExpectedConditions.urlContains("/calendar"));

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Team Sync')]")));

        logout();

        login("invitee", RAW_PASSWORD);

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Pending invites')]")));

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Team Sync')]")));

        clickButtonByText("Accept");

        wait.until(ExpectedConditions.urlContains("/calendar"));

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Team Sync')]")));

        assertThat(driver.getPageSource()).contains("Team Sync");
        assertThat(driver.getPageSource()).contains("invitee");
    }

    /*
     * E2E workflow 4:
     * User creates a meeting and then tries to create another one
     * with overlapping time. The conflicting meeting must not be created.
     */
    @Test
    void createMeetingWithOverlappingTime_doesNotCreateConflictingMeeting() {
        createUser("gustavo", "gustavo@email.com");

        login("gustavo", RAW_PASSWORD);

        createMeetingThroughUi(
                "First Meeting",
                "Initial meeting",
                "2026-06-22T10:00",
                "2026-06-22T11:00",
                null
        );

        wait.until(ExpectedConditions.urlContains("/calendar"));

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'First Meeting')]")));

        createMeetingThroughUi(
                "Conflicting Meeting",
                "This meeting overlaps with another one",
                "2026-06-22T10:30",
                "2026-06-22T11:30",
                null
        );

        assertThat(meetingRepository.findAll())
                .extracting("title")
                .contains("First Meeting")
                .doesNotContain("Conflicting Meeting");
    }

    private User createUser(String username, String email) {
        User user = new User(
                username,
                email,
                passwordEncoder.encode(RAW_PASSWORD)
        );

        return userRepository.saveAndFlush(user);
    }

    private void login(String username, String password) {
        driver.get(baseUrl() + "/login");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));

        typeById("username", username);
        typeById("password", password);

        clickButtonByText("Sign in");

        wait.until(ExpectedConditions.urlContains("/calendar"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Your calendar')]")));
    }

    private void logout() {
        clickButtonByText("Sign out");

        wait.until(ExpectedConditions.urlContains("/login"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
    }

    private void createMeetingThroughUi(
            String title,
            String description,
            String start,
            String end,
            String participantUsername) {

        driver.get(baseUrl() + "/meetings/new");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("title")));

        typeById("title", title);
        typeById("description", description);

        setInputValueById("start", start);
        setInputValueById("end", end);

        if (participantUsername != null && !participantUsername.isBlank()) {
            typeById("invitees", participantUsername);
        }

        clickButtonByText("Propose");
    }

    private void typeById(String id, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));

        element.clear();
        element.sendKeys(value);
    }

    private void setInputValueById(String id, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));

        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                        "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                element,
                value
        );
    }

    private void clickButtonByText(String text) {
        WebElement button = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='" + text + "']")));

        button.click();
    }
}