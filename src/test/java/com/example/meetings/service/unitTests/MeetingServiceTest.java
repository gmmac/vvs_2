package com.example.meetings.service.unitTests;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.MeetingService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

        private final String ORGANIZER_USERNAME = "gustavo";
        private final String ORGANIZER_EMAIL = "gustavo@example.com";
        private final String INVITEE_USERNAME = "machado";
        private final String INVITEE_EMAIL = "machado@example.com";
        private final String PASSWORD_HASH = "hash";

        private final Instant MEETING_START = Instant.parse("2026-06-20T10:00:00Z");
        private final Instant MEETING_END = Instant.parse("2026-06-20T11:00:00Z");

        @Mock
        private MeetingRepository meetingRepository;

        @Mock
        private MeetingParticipantRepository participantRepository;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private MeetingService meetingService;

        /*
         * Tests method propose
         * Condition: meeting data is valid and invitee exists
         */
        @Test
        void propose_validMeeting_createsMeeting() {
                User organizer = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);
                User invitee = new User(INVITEE_USERNAME, INVITEE_EMAIL, PASSWORD_HASH);

                when(userRepository.findByUsername(INVITEE_USERNAME)).thenReturn(Optional.of(invitee));
                when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Meeting result = meetingService.propose(
                                organizer,
                                "Project meeting",
                                "Discuss assignment",
                                MEETING_START,
                                MEETING_END,
                                List.of(INVITEE_USERNAME));

                assertThat(result.getTitle()).isEqualTo("Project meeting");
                assertThat(result.getDescription()).isEqualTo("Discuss assignment");
                assertThat(result.getStartTime()).isEqualTo(MEETING_START);
                assertThat(result.getEndTime()).isEqualTo(MEETING_END);
                assertThat(result.getOrganizer()).isSameAs(organizer);
                assertThat(result.getParticipants()).hasSize(2);

                assertThat(result.getParticipants())
                                .anyMatch(p -> p.getUser().getUsername().equals(ORGANIZER_USERNAME)
                                                && p.getStatus() == InviteStatus.ACCEPTED);

                assertThat(result.getParticipants())
                                .anyMatch(p -> p.getUser().getUsername().equals(INVITEE_USERNAME)
                                                && p.getStatus() == InviteStatus.PENDING);

                verify(userRepository).findByUsername(INVITEE_USERNAME);
                verify(meetingRepository).save(any(Meeting.class));
        }

        /*
         * Tests method propose
         * Condition: end time is before start time
         */
        @Test
        void propose_endBeforeStart_throwsException() {
                User organizer = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);

                Instant start = Instant.parse("2026-06-20T11:00:00Z");
                Instant end = Instant.parse("2026-06-20T10:00:00Z");

                assertThatThrownBy(() -> meetingService.propose(
                                organizer,
                                "Invalid meeting",
                                "",
                                start,
                                end,
                                List.of()))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("End time must be after start time");

                verify(meetingRepository, never()).save(any(Meeting.class));
        }

        /*
         * Tests method propose
         * Condition: end time is equal to start time
         */
        @Test
        void propose_endEqualsStart_throwsException() {
                User organizer = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);

                assertThatThrownBy(() -> meetingService.propose(
                                organizer,
                                "Invalid meeting",
                                "",
                                MEETING_START,
                                MEETING_START,
                                List.of()))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("End time must be after start time");

                verify(meetingRepository, never()).save(any(Meeting.class));
        }

        /*
         * Tests method propose
         * Condition: invitee does not exist
         */
        @Test
        void propose_unknownInvitee_throwsException() {
                User organizer = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);
                String unknownUsername = "unknown";

                when(userRepository.findByUsername(unknownUsername)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> meetingService.propose(
                                organizer,
                                "Meeting",
                                "",
                                MEETING_START,
                                MEETING_END,
                                List.of(unknownUsername)))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Unknown invitee: unknown");

                verify(userRepository).findByUsername(unknownUsername);
                verify(meetingRepository, never()).save(any(Meeting.class));
        }

        /*
         * Tests method propose
         * Condition: invitee list contains duplicates and organizer username
         */
        @Test
        void propose_duplicateInvitees_ignoresDuplicates() {
                User organizer = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);
                User invitee = new User(INVITEE_USERNAME, INVITEE_EMAIL, PASSWORD_HASH);

                when(userRepository.findByUsername(INVITEE_USERNAME)).thenReturn(Optional.of(invitee));
                when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Meeting result = meetingService.propose(
                                organizer,
                                "Meeting",
                                "",
                                MEETING_START,
                                MEETING_END,
                                List.of(ORGANIZER_USERNAME, INVITEE_USERNAME, INVITEE_USERNAME, " "));

                assertThat(result.getParticipants()).hasSize(2);

                verify(userRepository, times(1)).findByUsername(INVITEE_USERNAME);
                verify(userRepository, never()).findByUsername(ORGANIZER_USERNAME);
                verify(meetingRepository).save(any(Meeting.class));
        }

        /*
         * Tests method calendarFor
         * Condition: repository returns calendar meetings
         */
        @Test
        void calendarFor_existingUser_returnsRepositoryResult() {
                User user = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);
                List<Meeting> meetings = List.of();

                when(meetingRepository.findCalendarMeetings(user)).thenReturn(meetings);

                List<Meeting> result = meetingService.calendarFor(user);

                assertThat(result).isSameAs(meetings);

                verify(meetingRepository).findCalendarMeetings(user);
        }

        /*
         * Tests method pendingInvitesFor
         * Condition: repository returns pending invites
         */
        @Test
        void pendingInvitesFor_existingUser_returnsPendingInvites() {
                User user = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);
                List<MeetingParticipant> pendingInvites = List.of();

                when(participantRepository.findByUserAndStatus(user, InviteStatus.PENDING))
                                .thenReturn(pendingInvites);

                List<MeetingParticipant> result = meetingService.pendingInvitesFor(user);

                assertThat(result).isSameAs(pendingInvites);

                verify(participantRepository).findByUserAndStatus(user, InviteStatus.PENDING);
        }

        /*
         * Tests method respond
         * Condition: invite exists and response is ACCEPTED
         */
        @Test
        void respond_acceptInvite_updatesStatus() {
                User user = new User(INVITEE_USERNAME, INVITEE_EMAIL, PASSWORD_HASH);
                Meeting meeting = new Meeting("Meeting", "", MEETING_START, MEETING_END, user);
                MeetingParticipant participant = new MeetingParticipant(meeting, user, InviteStatus.PENDING);

                when(participantRepository.findByMeetingIdAndUserId(1L, user.getId()))
                                .thenReturn(Optional.of(participant));

                meetingService.respond(1L, user, InviteStatus.ACCEPTED);

                assertThat(participant.getStatus()).isEqualTo(InviteStatus.ACCEPTED);

                verify(participantRepository).findByMeetingIdAndUserId(1L, user.getId());
        }

        /*
         * Tests method respond
         * Condition: invite exists and response is DECLINED
         */
        @Test
        void respond_declineInvite_updatesStatus() {
                User user = new User(INVITEE_USERNAME, INVITEE_EMAIL, PASSWORD_HASH);
                Meeting meeting = new Meeting("Meeting", "", MEETING_START, MEETING_END, user);
                MeetingParticipant participant = new MeetingParticipant(meeting, user, InviteStatus.PENDING);

                when(participantRepository.findByMeetingIdAndUserId(1L, user.getId()))
                                .thenReturn(Optional.of(participant));

                meetingService.respond(1L, user, InviteStatus.DECLINED);

                assertThat(participant.getStatus()).isEqualTo(InviteStatus.DECLINED);

                verify(participantRepository).findByMeetingIdAndUserId(1L, user.getId());
        }

        /*
         * Tests method respond
         * Condition: response status is PENDING
         */
        @Test
        void respond_pendingStatus_throwsException() {
                User user = new User(INVITEE_USERNAME, INVITEE_EMAIL, PASSWORD_HASH);

                assertThatThrownBy(() -> meetingService.respond(1L, user, InviteStatus.PENDING))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Response must be ACCEPTED or DECLINED");

                verify(participantRepository, never()).findByMeetingIdAndUserId(anyLong(), any());
        }

        /*
         * Tests method respond
         * Condition: invite does not exist
         */
        @Test
        void respond_missingInvite_throwsException() {
                User user = new User(INVITEE_USERNAME, INVITEE_EMAIL, PASSWORD_HASH);

                when(participantRepository.findByMeetingIdAndUserId(1L, user.getId()))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> meetingService.respond(1L, user, InviteStatus.ACCEPTED))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("No invite found for this user");

                verify(participantRepository).findByMeetingIdAndUserId(1L, user.getId());
        }

        /*
         * Tests method copyFromDiscovered
         * Condition: discovered event has no end time
         */
        @Test
        void copyFromDiscovered_missingEnd_createsTwoHourMeeting() {
                User user = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);

                DiscoveredEvent event = new DiscoveredEvent(
                                "Ticketmaster",
                                "tm-1",
                                "Jazz Night",
                                "Live concert",
                                Instant.parse("2026-06-20T20:00:00Z"),
                                null,
                                "https://example.com/event",
                                "Lisbon Arena");

                when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Meeting result = meetingService.copyFromDiscovered(user, event);

                assertThat(result.getTitle()).isEqualTo("Jazz Night");
                assertThat(result.getStartTime()).isEqualTo(Instant.parse("2026-06-20T20:00:00Z"));
                assertThat(result.getEndTime()).isEqualTo(Instant.parse("2026-06-20T22:00:00Z"));
                assertThat(result.getOrganizer()).isSameAs(user);
                assertThat(result.getParticipants()).hasSize(1);
                assertThat(result.isConfirmed()).isTrue();
                assertThat(result.getDescription()).contains("Live concert");
                assertThat(result.getDescription()).contains("Venue: Lisbon Arena");
                assertThat(result.getDescription()).contains("Source: Ticketmaster");
                assertThat(result.getDescription()).contains("https://example.com/event");

                verify(meetingRepository).save(any(Meeting.class));
        }

        /*
         * Tests method copyFromDiscovered
         * Condition: discovered event has an end time
         */
        @Test
        void copyFromDiscovered_existingEnd_usesEventEnd() {
                User user = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);

                DiscoveredEvent event = new DiscoveredEvent(
                                "SeatGeek",
                                "sg-1",
                                "Football Match",
                                null,
                                Instant.parse("2026-06-20T20:00:00Z"),
                                Instant.parse("2026-06-20T21:30:00Z"),
                                null,
                                null);

                when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Meeting result = meetingService.copyFromDiscovered(user, event);

                assertThat(result.getTitle()).isEqualTo("Football Match");
                assertThat(result.getStartTime()).isEqualTo(Instant.parse("2026-06-20T20:00:00Z"));
                assertThat(result.getEndTime()).isEqualTo(Instant.parse("2026-06-20T21:30:00Z"));
                assertThat(result.getDescription()).contains("Source: SeatGeek");
                assertThat(result.isConfirmed()).isTrue();

                verify(meetingRepository).save(any(Meeting.class));
        }

        /*
         * Tests method calendarForIcalToken
         * Condition: token is valid
         */
        @Test
        void calendarForIcalToken_validToken_returnsCalendar() {
                User user = new User(ORGANIZER_USERNAME, ORGANIZER_EMAIL, PASSWORD_HASH);
                List<Meeting> meetings = List.of();

                when(userRepository.findByIcalToken(user.getIcalToken())).thenReturn(Optional.of(user));
                when(meetingRepository.findCalendarMeetings(user)).thenReturn(meetings);

                List<Meeting> result = meetingService.calendarForIcalToken(user.getIcalToken());

                assertThat(result).isEmpty();

                verify(userRepository).findByIcalToken(user.getIcalToken());
                verify(meetingRepository).findCalendarMeetings(user);
        }

        /*
         * Tests method calendarForIcalToken
         * Condition: token is invalid
         */
        @Test
        void calendarForIcalToken_invalidToken_throwsException() {
                String invalidToken = "invalid-token";

                when(userRepository.findByIcalToken(invalidToken)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> meetingService.calendarForIcalToken(invalidToken))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Invalid iCal token");

                verify(userRepository).findByIcalToken(invalidToken);
        }
}