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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class MeetingParticipantRepositoryDatabaseIntegrationTest {

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
    private MeetingParticipantRepository meetingParticipantRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    /*
     * Tests method findByUserAndStatus
     * Condition: user has one pending invite and one accepted invite
     */
    @Test
    void findByUserAndStatus_existingPendingInvite_returnsParticipant() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        Meeting pendingMeeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        Meeting acceptedMeeting = meetingRepository.save(new Meeting(
                "Accepted Meeting",
                EXAMPLE_DESCRIPTION,
                Instant.parse("2026-06-21T10:00:00Z"),
                Instant.parse("2026-06-21T11:00:00Z"),
                organizer));

        MeetingParticipant pendingParticipant = meetingParticipantRepository.save(
                new MeetingParticipant(
                        pendingMeeting,
                        invitee,
                        InviteStatus.PENDING));

        meetingParticipantRepository.save(
                new MeetingParticipant(
                        acceptedMeeting,
                        invitee,
                        InviteStatus.ACCEPTED));

        List<MeetingParticipant> result = meetingParticipantRepository.findByUserAndStatus(
                invitee,
                InviteStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(pendingParticipant.getId());
        assertThat(result.get(0).getUser().getUsername()).isEqualTo(INVITEE_USERNAME);
        assertThat(result.get(0).getStatus()).isEqualTo(InviteStatus.PENDING);
    }

    /*
     * Tests method findByMeetingIdAndUserId
     * Condition: participant exists for meeting and user
     */
    @Test
    void findByMeetingIdAndUserId_existingParticipant_returnsParticipant() {
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

        MeetingParticipant participant = meetingParticipantRepository.save(
                new MeetingParticipant(
                        meeting,
                        invitee,
                        InviteStatus.PENDING));

        Optional<MeetingParticipant> result = meetingParticipantRepository.findByMeetingIdAndUserId(
                meeting.getId(),
                invitee.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(participant.getId());
        assertThat(result.get().getMeeting().getId()).isEqualTo(meeting.getId());
        assertThat(result.get().getUser().getId()).isEqualTo(invitee.getId());
        assertThat(result.get().getStatus()).isEqualTo(InviteStatus.PENDING);
    }

    /*
     * Tests unique constraint on meeting_participants
     * Condition: same user is added twice to the same meeting
     */
    @Test
    void save_duplicateMeetingAndUser_throwsException() {
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

        meetingParticipantRepository.saveAndFlush(
                new MeetingParticipant(
                        meeting,
                        invitee,
                        InviteStatus.PENDING));

        assertThatThrownBy(() -> meetingParticipantRepository.saveAndFlush(
                new MeetingParticipant(
                        meeting,
                        invitee,
                        InviteStatus.ACCEPTED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /*
     * Tests method findByUserAndStatus
     * Condition: another user has a pending invite
     */
    @Test
    void findByUserAndStatus_otherUserInvite_doesNotReturnParticipant() {
        User organizer = userRepository.save(new User(
                ORGANIZER_USERNAME,
                ORGANIZER_EMAIL,
                ORGANIZER_PASSWORD_HASH));

        User invitee = userRepository.save(new User(
                INVITEE_USERNAME,
                INVITEE_EMAIL,
                INVITEE_PASSWORD_HASH));

        User otherUser = userRepository.save(new User(
                OTHER_USERNAME,
                OTHER_EMAIL,
                OTHER_PASSWORD_HASH));

        Meeting meeting = meetingRepository.save(new Meeting(
                EXAMPLE_TITLE,
                EXAMPLE_DESCRIPTION,
                EXAMPLE_START,
                EXAMPLE_END,
                organizer));

        meetingParticipantRepository.save(new MeetingParticipant(
                meeting,
                otherUser,
                InviteStatus.PENDING));

        List<MeetingParticipant> result = meetingParticipantRepository.findByUserAndStatus(
                invitee,
                InviteStatus.PENDING);

        assertThat(result).isEmpty();
    }

}