package com.gametrust.backend.service;

import com.gametrust.backend.exception.UnauthorizedException;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveKitWebhookServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private LiveKitWebhookVerifier verifier;

    private LiveKitWebhookService service;

    @BeforeEach
    void setUp() {
        service = new LiveKitWebhookService(mongoTemplate, verifier);
    }

    @Test
    void verifiedParticipantJoinedUpsertsPresenceAndCreatesAudit() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("community_channels")))
                .thenReturn(channel());
        when(mongoTemplate.exists(any(Query.class), eq("voice_session_audit"))).thenReturn(false);

        Map<String, Object> result = service.processVerifiedEvent(event("participant_joined", true));

        assertTrue((Boolean) result.get("processed"));
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq("voice_room_members"));
        verify(mongoTemplate).insert(any(Document.class), eq("voice_session_audit"));
    }

    @Test
    void verifiedParticipantLeftRemovesPresenceAndClosesAudit() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("community_channels")))
                .thenReturn(channel());

        Map<String, Object> result = service.processVerifiedEvent(event("participant_left", true));

        assertTrue((Boolean) result.get("processed"));
        verify(mongoTemplate).remove(any(Query.class), eq("voice_room_members"));
        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq("voice_session_audit"));
    }

    @Test
    void ignoresUnrelatedVerifiedEventWithoutMutatingPresence() {
        Map<String, Object> result = service.processVerifiedEvent(event("track_published", true));
        assertFalse((Boolean) result.get("processed"));
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq("voice_room_members"));
    }

    @Test
    void rejectsInvalidWebhookSignature() {
        when(verifier.decode("{}", "bad-token")).thenThrow(new IllegalArgumentException("invalid"));
        assertThrows(UnauthorizedException.class, () -> service.receive("{}", "bad-token"));
    }

    @Test
    void rejectsMissingWebhookSignatureBeforeDecoding() {
        assertThrows(UnauthorizedException.class, () -> service.receive("{}", null));
        verify(verifier, never()).decode(any(), any());
    }

    @Test
    void returnsVerifiedEventIdentity() {
        LivekitWebhook.WebhookEvent decoded = event("track_published", true);
        when(verifier.decode("body", "Bearer valid")).thenReturn(decoded);
        Map<String, Object> result = service.receive("body", "Bearer valid");
        assertEquals("evt-1", result.get("eventId"));
        assertEquals("track_published", result.get("event"));
    }

    private LivekitWebhook.WebhookEvent event(String name, boolean participant) {
        LivekitWebhook.WebhookEvent.Builder builder = LivekitWebhook.WebhookEvent.newBuilder()
                .setId("evt-1")
                .setEvent(name)
                .setRoom(LivekitModels.Room.newBuilder()
                        .setName("voice_valorant_voice_1")
                        .setSid("RM_1"));
        if (participant) {
            builder.setParticipant(LivekitModels.ParticipantInfo.newBuilder()
                    .setSid("PA_1")
                    .setIdentity("user-123")
                    .setName("playerOne"));
        }
        return builder.build();
    }

    private Document channel() {
        return new Document()
                .append("id", "valorant_voice_1")
                .append("gameId", "valorant")
                .append("livekitRoomName", "voice_valorant_voice_1");
    }
}
