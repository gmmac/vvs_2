package com.example.meetings.service.unitTests;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.service.ICalService;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalServiceTest {

        private final String EXAMPLE_USERNAME = "gustavo";
        private final String EXAMPLE_EMAIL = "gustavo@email.com";
        private final String EXAMPLE_PASSWORD_HASH = "hash_pswd";

        private final String INVITEE_USERNAME = "machado";
        private final String INVITEE_EMAIL = "machado@email.com";

        private final Instant EXAMPLE_START = Instant.parse("2026-06-20T10:00:00Z");
        private final Instant EXAMPLE_END = Instant.parse("2026-06-20T11:00:00Z");

        private final ICalService icalService = new ICalService();

        /*
         * Tests method render
         * Condition: calendar has no meetings
         */
        @Test
        void render_noMeetings_generatesCalendarStructure() {
                User user = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

                String result = icalService.render(user, List.of());

                assertThat(result).contains("BEGIN:VCALENDAR");
                assertThat(result).contains("VERSION:2.0");
                assertThat(result).contains("PRODID:-//meetings-app//EN");
                assertThat(result).contains("CALSCALE:GREGORIAN");
                assertThat(result).contains("METHOD:PUBLISH");
                assertThat(result).contains("X-WR-CALNAME:gustavo's meetings");
                assertThat(result).contains("END:VCALENDAR");
        }

        /*
         * Tests method render
         * Condition: calendar output is generated
         */
        @Test
        void render_calendarOutput_usesCrlfLineEndings() {
                User user = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

                String result = icalService.render(user, List.of());

                assertThat(result).contains("BEGIN:VCALENDAR\r\n");
                assertThat(result).contains("VERSION:2.0\r\n");
                assertThat(result).contains("END:VCALENDAR\r\n");
        }


        /*
         * Tests method render
         * Condition: meeting has all participants accepted
         */
        @Test
        void render_confirmedMeeting_generatesConfirmedEvent() {
                User organizer = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

                Meeting meeting = new Meeting(
                                "Project meeting",
                                "Discuss VVS assignment",
                                EXAMPLE_START,
                                EXAMPLE_END,
                                organizer);

                meeting.addParticipant(
                                new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));

                String result = icalService.render(organizer, List.of(meeting));

                assertThat(result).contains("BEGIN:VEVENT");
                assertThat(result).contains("DTSTART:20260620T100000Z");
                assertThat(result).contains("DTEND:20260620T110000Z");
                assertThat(result).contains("SUMMARY:Project meeting");
                assertThat(result).contains("DESCRIPTION:Discuss VVS assignment");
                assertThat(result).contains("ORGANIZER;CN=gustavo:mailto:gustavo@email.com");
                assertThat(result).contains("ATTENDEE;CN=gustavo;PARTSTAT=ACCEPTED:mailto:gustavo@email.com");
                assertThat(result).contains("STATUS:CONFIRMED");
                assertThat(result).contains("END:VEVENT");
        }

        /*
         * Tests method render
         * Condition: meeting has a pending invite
         */
        @Test
        void render_pendingInvite_generatesTentativeEvent() {
                User organizer = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);
                User invitee = new User(INVITEE_USERNAME, INVITEE_EMAIL, EXAMPLE_PASSWORD_HASH);

                Meeting meeting = new Meeting(
                                "Pending meeting",
                                "Waiting for invitee",
                                EXAMPLE_START,
                                EXAMPLE_END,
                                organizer);

                meeting.addParticipant(
                                new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
                meeting.addParticipant(
                                new MeetingParticipant(meeting, invitee, InviteStatus.PENDING));

                String result = icalService.render(organizer, List.of(meeting));

                assertThat(result).contains("SUMMARY:Pending meeting");
                assertThat(result).contains("ATTENDEE;CN=gustavo;PARTSTAT=ACCEPTED:mailto:gustavo@email.com");
                assertThat(result).contains("ATTENDEE;CN=machado;PARTSTAT=NEEDS-ACTION:mailto:machado@email.com");
                assertThat(result).contains("STATUS:TENTATIVE");
        }

        /*
         * Tests method render
         * Condition: meeting has a declined participant
         */
        @Test
        void render_declinedInvite_generatesDeclinedParticipant() {
                User organizer = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);
                User invitee = new User(INVITEE_USERNAME, INVITEE_EMAIL, EXAMPLE_PASSWORD_HASH);

                Meeting meeting = new Meeting(
                                "Declined meeting",
                                "Invitee declined",
                                EXAMPLE_START,
                                EXAMPLE_END,
                                organizer);

                meeting.addParticipant(
                                new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
                meeting.addParticipant(
                                new MeetingParticipant(meeting, invitee, InviteStatus.DECLINED));

                String result = icalService.render(organizer, List.of(meeting));

                assertThat(result).contains("ATTENDEE;CN=machado;PARTSTAT=DECLINED:mailto:machado@email.com");
                assertThat(result).contains("STATUS:TENTATIVE");
        }

        /*
         * Tests method render
         * Condition: meeting description is blank
         */
        @Test
        void render_blankDescription_omitsDescription() {
                User organizer = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

                Meeting meeting = new Meeting(
                                "Meeting without description",
                                "",
                                EXAMPLE_START,
                                EXAMPLE_END,
                                organizer);

                meeting.addParticipant(
                                new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));

                String result = icalService.render(organizer, List.of(meeting));

                assertThat(result).contains("SUMMARY:Meeting without description");
                assertThat(result).doesNotContain("DESCRIPTION:");
        }

        /*
         * Tests method render
         * Condition: summary and description contain special iCal characters
         */
        @Test
        void render_specialChars_escapesSummaryAndDescription() {
                User organizer = new User(EXAMPLE_USERNAME, EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

                Meeting meeting = new Meeting(
                                "Meeting, with; chars",
                                "Line 1\nLine 2",
                                EXAMPLE_START,
                                EXAMPLE_END,
                                organizer);

                meeting.addParticipant(
                                new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));

                String result = icalService.render(organizer, List.of(meeting));

                assertThat(result).contains("SUMMARY:Meeting\\, with\\; chars");
                assertThat(result).contains("DESCRIPTION:Line 1\\nLine 2");
        }

        /*
         * Tests method render
         * Condition: user fields contain special iCal characters
         */
        @Test
        void render_specialCharsInUser_escapesUserFields() {
                User organizer = new User("gustavo,admin", EXAMPLE_EMAIL, EXAMPLE_PASSWORD_HASH);

                Meeting meeting = new Meeting(
                                "Meeting",
                                "Description",
                                EXAMPLE_START,
                                EXAMPLE_END,
                                organizer);

                meeting.addParticipant(
                                new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));

                String result = icalService.render(organizer, List.of(meeting));

                assertThat(result).contains("X-WR-CALNAME:gustavo\\,admin's meetings");
                assertThat(result).contains("ORGANIZER;CN=gustavo\\,admin:mailto:gustavo@email.com");
                assertThat(result).contains("ATTENDEE;CN=gustavo\\,admin;PARTSTAT=ACCEPTED:mailto:gustavo@email.com");
        }
}