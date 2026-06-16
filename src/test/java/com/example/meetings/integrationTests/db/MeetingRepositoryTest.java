package com.example.meetings.integrationTests.db;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MeetingRepositoryDatabaseIntegrationTest {

    private final String ORGANIZER_USERNAME = "gustavo";
    private final String ORGANIZER_EMAIL = "gustavo@email.com";
    private final String ORGANIZER_PASSWORD_HASH = "hash_pswd";

    private final String INVITEE_USERNAME = "macedo";
    private final String INVITEE_EMAIL = "macedo@email.com";
    private final String INVITEE_PASSWORD_HASH = "hash_pswd";

    private final String OTHER_USERNAME = "machado";
    private final String OTHER_EMAIL = "machado@email.com";
    private final String OTHER_PASSWORD_HASH = "hash_pswd";

    private final String EXAMPLE_TITLE = "Project Meeting";
    private final String EXAMPLE_DESCRIPTION = "Discuss project progress";

    private final Instant EXAMPLE_START = Instant.parse("2026-06-20T10:00:00Z");
    private final Instant EXAMPLE_END = Instant.parse("2026-06-20T11:00:00Z");

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository meetingParticipantRepository;

    @Autowired
    private UserRepository userRepository;

    /*
     * Tests method findCalendarMeetings
     * Condition: user is the organizer of a meeting
     */
    @Test
    void findCalendarMeetings_userIsOrganizer_returnsMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        List<Meeting> result = meetingRepository.findCalendarMeetings(organizer);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(meeting.getId());
        assertThat(result.get(0).getOrganizer().getId()).isEqualTo(organizer.getId());
        assertThat(result.get(0).getTitle()).isEqualTo(EXAMPLE_TITLE);
    }

    /*
     * Tests method findCalendarMeetings
     * Condition: user is an accepted participant of a meeting
     */
    @Test
    void findCalendarMeetings_userIsAcceptedParticipant_returnsMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        meetingParticipantRepository.save(new MeetingParticipant(
                meeting,
                invitee,
                InviteStatus.ACCEPTED));

        List<Meeting> result = meetingRepository.findCalendarMeetings(invitee);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(meeting.getId());
        assertThat(result.get(0).getTitle()).isEqualTo(EXAMPLE_TITLE);
    }

    /*
     * Tests method findCalendarMeetings
     * Condition: user is a pending participant of a meeting
     */
    @Test
    void findCalendarMeetings_userIsPendingParticipant_returnsMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        meetingParticipantRepository.save(new MeetingParticipant(
                meeting,
                invitee,
                InviteStatus.PENDING));

        List<Meeting> result = meetingRepository.findCalendarMeetings(invitee);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(meeting.getId());
        assertThat(result.get(0).getTitle()).isEqualTo(EXAMPLE_TITLE);
    }

    /*
     * Tests method findCalendarMeetings
     * Condition: user declined the invite
     */
    @Test
    void findCalendarMeetings_userDeclinedInvite_doesNotReturnMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        meetingParticipantRepository.save(new MeetingParticipant(
                meeting,
                invitee,
                InviteStatus.DECLINED));

        List<Meeting> result = meetingRepository.findCalendarMeetings(invitee);

        assertThat(result).isEmpty();
    }

    /*
     * Tests method findCalendarMeetings
     * Condition: user is neither organizer nor participant
     */
    @Test
    void findCalendarMeetings_unrelatedUser_doesNotReturnMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User otherUser = userRepository.save(new User(
                OTHER_USERNAME,
                OTHER_EMAIL,
                OTHER_PASSWORD_HASH));

        meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        List<Meeting> result = meetingRepository.findCalendarMeetings(otherUser);

        assertThat(result).isEmpty();
    }

    /*
     * Tests method findCalendarMeetings
     * Condition: meetings should be ordered by start time
     */
    @Test
    void findCalendarMeetings_multipleMeetings_returnsOrderedByStartTime() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        Meeting laterMeeting = meetingRepository.save(new Meeting(
                "Later Meeting",
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T15:00:00Z"),
                Instant.parse("2026-06-20T16:00:00Z"),
                organizer));

        Meeting earlierMeeting = meetingRepository.save(new Meeting(
                "Earlier Meeting",
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T09:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z"),
                organizer));

        List<Meeting> result = meetingRepository.findCalendarMeetings(organizer);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(earlierMeeting.getId());
        assertThat(result.get(1).getId()).isEqualTo(laterMeeting.getId());
    }

    /*
     * Tests method findOverlapping
     * Condition: meeting overlaps with the given time interval
     */
    @Test
    void findOverlapping_existingOverlappingMeeting_returnsMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T11:00:00Z"),
                organizer));

        List<Meeting> result = meetingRepository.findOverlapping(
                organizer,
                Instant.parse("2026-06-20T10:30:00Z"),
                Instant.parse("2026-06-20T11:30:00Z"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(meeting.getId());
    }

    /*
     * Tests method findOverlapping
     * Condition: meeting ends exactly when the searched interval starts
     */
    @Test
    void findOverlapping_meetingEndsWhenIntervalStarts_doesNotReturnMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T11:00:00Z"),
                organizer));

        List<Meeting> result = meetingRepository.findOverlapping(
                organizer,
                Instant.parse("2026-06-20T11:00:00Z"),
                Instant.parse("2026-06-20T12:00:00Z"));

        assertThat(result).isEmpty();
    }

    /*
     * Tests method findOverlapping
     * Condition: meeting starts exactly when the searched interval ends
     */
    @Test
    void findOverlapping_meetingStartsWhenIntervalEnds_doesNotReturnMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T11:00:00Z"),
                organizer));

        List<Meeting> result = meetingRepository.findOverlapping(
                organizer,
                Instant.parse("2026-06-20T09:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z"));

        assertThat(result).isEmpty();
    }

    /*
     * Tests method findOverlapping
     * Condition: user declined the invite, so meeting should not block calendar
     */
    @Test
    void findOverlapping_declinedParticipant_doesNotReturnMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T11:00:00Z"),
                organizer));

        Meeting meeting = meetingRepository.save(new Meeting(
                "Declined Meeting",
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T12:00:00Z"),
                Instant.parse("2026-06-20T13:00:00Z"),
                organizer));

        meetingParticipantRepository.save(new MeetingParticipant(
                meeting,
                invitee,
                InviteStatus.DECLINED));

        List<Meeting> result = meetingRepository.findOverlapping(
                invitee,
                Instant.parse("2026-06-20T12:30:00Z"),
                Instant.parse("2026-06-20T13:30:00Z"));

        assertThat(result).isEmpty();
    }

    /*
     * Tests method findOverlapping
     * Condition: user is accepted participant and meeting overlaps with interval
     */
    @Test
    void findOverlapping_acceptedParticipantOverlappingMeeting_returnsMeeting() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T11:00:00Z"),
                organizer));

        meetingParticipantRepository.save(new MeetingParticipant(
                meeting,
                invitee,
                InviteStatus.ACCEPTED));

        List<Meeting> result = meetingRepository.findOverlapping(
                invitee,
                Instant.parse("2026-06-20T10:30:00Z"),
                Instant.parse("2026-06-20T10:45:00Z"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(meeting.getId());
    }
}