package com.gametrust.backend.service;

import com.gametrust.backend.config.LiveKitProperties;
import com.gametrust.backend.dto.community.VoiceJoinResponse;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.ResourceNotFoundException;
import com.gametrust.backend.security.UserPrincipal;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiveKitVoiceService {

    private static final String CHANNELS = "community_channels";
    private static final String VOICE_MEMBERS = "voice_room_members";

    private final MongoTemplate mongoTemplate;
    private final LiveKitProperties properties;

    public LiveKitVoiceService(MongoTemplate mongoTemplate, LiveKitProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    public VoiceJoinResponse createJoinToken(String roomId, UserPrincipal user) {
        validateConfiguration();
        Document room = mongoTemplate.findOne(
                Query.query(Criteria.where("id").is(roomId)), Document.class, CHANNELS);
        if (room == null) {
            throw new ResourceNotFoundException("Voice room not found");
        }
        if (!"VOICE".equals(room.getString("type"))) {
            throw new BadRequestException("Channel is not a voice room");
        }
        if (Boolean.TRUE.equals(room.getBoolean("locked")) && !canManage(room, user)) {
            throw new AccessDeniedException("This voice room is locked");
        }

        long online = mongoTemplate.count(Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS);
        int capacity = room.getInteger("capacity", 10);
        boolean alreadyPresent = mongoTemplate.exists(new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(roomId),
                Criteria.where("userId").is(user.getId())
        )), VOICE_MEMBERS);
        if (!alreadyPresent && online >= capacity) {
            throw new BadRequestException("This voice room is full");
        }

        String livekitRoomName = room.getString("livekitRoomName");
        if (livekitRoomName == null || livekitRoomName.isBlank()) {
            livekitRoomName = "voice_" + roomId.replaceAll("[^A-Za-z0-9_-]", "_");
        }

        AccessToken token = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        token.setIdentity(user.getId());
        token.setName(user.getUsername());
        token.setTtl(properties.getTokenTtlSeconds() * 1000L);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(livekitRoomName),
                new CanPublish(true),
                new CanPublishSources(List.of("microphone")),
                new CanSubscribe(true),
                new CanPublishData(false)
        );

        return new VoiceJoinResponse(
                properties.getServerUrl(),
                token.toJwt(),
                properties.getTokenTtlSeconds(),
                roomId,
                livekitRoomName
        );
    }

    private void validateConfiguration() {
        if (isBlank(properties.getServerUrl()) || isBlank(properties.getApiKey()) || isBlank(properties.getApiSecret())) {
            throw new IllegalStateException("LiveKit is not configured on the backend");
        }
        if (properties.getTokenTtlSeconds() < 30 || properties.getTokenTtlSeconds() > 900) {
            throw new IllegalStateException("LiveKit token TTL must be between 30 and 900 seconds");
        }
    }

    private boolean canManage(Document room, UserPrincipal user) {
        boolean privileged = user.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_MODERATOR".equals(authority.getAuthority()));
        return privileged || user.getId().equals(room.getString("ownerId"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
