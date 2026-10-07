package com.gametrust.backend.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.gametrust.backend.config.LiveKitProperties;
import com.gametrust.backend.dto.community.VoiceJoinResponse;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.ResourceNotFoundException;
import com.gametrust.backend.security.UserPrincipal;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveKitVoiceServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private LiveKitRoomAdminService liveKitRoomAdminService;

    private LiveKitProperties properties;
    private LiveKitVoiceService service;
    private UserPrincipal member;

    @BeforeEach
    void setUp() {
        properties = new LiveKitProperties();
        properties.setServerUrl("ws://localhost:7880");
        properties.setApiKey("devkey");
        properties.setApiSecret("secret");
        properties.setTokenTtlSeconds(300);
        service = new LiveKitVoiceService(mongoTemplate, properties, liveKitRoomAdminService);
        member = principal("user-123", "playerOne", "ROLE_MEMBER");
    }

    @Test
    void createsShortLivedMicrophoneOnlyTokenFromAuthenticatedUser() {
        whenRoom(voiceRoom(false, 10, "owner-1"));
        when(mongoTemplate.count(any(Query.class), eq("voice_room_members"))).thenReturn(2L);

        VoiceJoinResponse response = service.createJoinToken("valorant_voice_1", member);

        assertEquals("ws://localhost:7880", response.serverUrl());
        assertEquals("valorant_voice_1", response.roomId());
        assertEquals("voice_valorant_voice_1", response.livekitRoomName());
        assertEquals(300, response.expiresInSeconds());
        assertNotNull(response.participantToken());
        assertFalse(response.participantToken().contains(properties.getApiSecret()));

        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(properties.getApiSecret()))
                .withIssuer(properties.getApiKey())
                .build()
                .verify(response.participantToken());
        assertEquals("user-123", jwt.getSubject());
        assertEquals("playerOne", jwt.getClaim("name").asString());
        Map<String, Object> video = jwt.getClaim("video").asMap();
        assertEquals(true, video.get("roomJoin"));
        assertEquals(true, video.get("canPublish"));
        assertEquals(true, video.get("canSubscribe"));
        assertEquals(false, video.get("canPublishData"));
        assertEquals("voice_valorant_voice_1", video.get("room"));
        assertEquals(List.of("microphone"), video.get("canPublishSources"));
        assertNotNull(jwt.getExpiresAtAsInstant());
        assertTrue(jwt.getExpiresAtAsInstant().isAfter(Instant.now()));
        assertTrue(jwt.getExpiresAtAsInstant().isBefore(Instant.now().plusSeconds(301)));
    }

    @Test
    void rejectsUnknownRoom() {
        whenRoom(null);
        assertThrows(ResourceNotFoundException.class,
                () -> service.createJoinToken("missing", member));
    }

    @Test
    void rejectsTextChannel() {
        Document text = voiceRoom(false, 10, "owner-1");
        text.put("type", "TEXT");
        whenRoom(text);
        assertThrows(BadRequestException.class,
                () -> service.createJoinToken("valorant_general", member));
    }

    @Test
    void rejectsLockedRoomForNormalMember() {
        whenRoom(voiceRoom(true, 10, "owner-1"));
        assertThrows(AccessDeniedException.class,
                () -> service.createJoinToken("valorant_voice_1", member));
    }

    @Test
    void allowsLockedRoomForOwner() {
        whenRoom(voiceRoom(true, 10, "user-123"));
        when(mongoTemplate.count(any(Query.class), eq("voice_room_members"))).thenReturn(0L);
        assertNotNull(service.createJoinToken("valorant_voice_1", member).participantToken());
    }

    @Test
    void allowsLockedRoomForModerator() {
        UserPrincipal moderator = principal("mod-1", "moderator", "ROLE_MODERATOR");
        whenRoom(voiceRoom(true, 10, "owner-1"));
        when(mongoTemplate.count(any(Query.class), eq("voice_room_members"))).thenReturn(0L);
        assertNotNull(service.createJoinToken("valorant_voice_1", moderator).participantToken());
    }

    @Test
    void rejectsNewParticipantWhenRoomIsFull() {
        whenRoom(voiceRoom(false, 2, "owner-1"));
        when(mongoTemplate.count(any(Query.class), eq("voice_room_members"))).thenReturn(2L);
        assertThrows(BadRequestException.class,
                () -> service.createJoinToken("valorant_voice_1", member));
    }

    @Test
    void permitsReconnectWhenPresenceAlreadyExistsInFullRoom() {
        whenRoom(voiceRoom(false, 2, "owner-1"));
        when(mongoTemplate.count(any(Query.class), eq("voice_room_members"))).thenReturn(2L);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("voice_room_members")))
                .thenReturn(new Document("roomId", "valorant_voice_1"));
        assertNotNull(service.createJoinToken("valorant_voice_1", member).participantToken());
    }

    @Test
    void rejectsJoiningAnotherRoomWhilePresenceIsActive() {
        whenRoom(voiceRoom(false, 10, "owner-1"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("voice_room_members")))
                .thenReturn(new Document("roomId", "other-room"));

        assertThrows(BadRequestException.class,
                () -> service.createJoinToken("valorant_voice_1", member));
    }

    @Test
    void keepsServerMutedParticipantFromPublishingAfterReconnect() {
        whenRoom(voiceRoom(false, 10, "owner-1"));
        when(mongoTemplate.exists(any(Query.class), eq("voice_room_moderation"))).thenReturn(true);

        VoiceJoinResponse response = service.createJoinToken("valorant_voice_1", member);
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(properties.getApiSecret()))
                .withIssuer(properties.getApiKey()).build().verify(response.participantToken());
        Map<String, Object> video = jwt.getClaim("video").asMap();
        assertEquals(false, video.get("canPublish"));
        assertFalse(video.containsKey("canPublishSources"));
    }

    @Test
    void rejectsUnsafeTokenLifetimeConfiguration() {
        properties.setTokenTtlSeconds(3600);
        assertThrows(IllegalStateException.class,
                () -> service.createJoinToken("valorant_voice_1", member));
    }

    private void whenRoom(Document room) {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("community_channels")))
                .thenReturn(room);
    }

    private Document voiceRoom(boolean locked, int capacity, String ownerId) {
        return new Document()
                .append("id", "valorant_voice_1")
                .append("type", "VOICE")
                .append("ownerId", ownerId)
                .append("locked", locked)
                .append("capacity", capacity)
                .append("livekitRoomName", "voice_valorant_voice_1");
    }

    private UserPrincipal principal(String id, String username, String role) {
        return new UserPrincipal(
                id,
                username,
                username + "@example.com",
                "password",
                true,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
