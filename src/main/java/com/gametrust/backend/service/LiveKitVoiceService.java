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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiveKitVoiceService {

    private static final String CHANNELS = "community_channels";
    private static final String VOICE_MEMBERS = "voice_room_members";
    private static final String VOICE_MODERATION = "voice_room_moderation";

    private final MongoTemplate mongoTemplate;
    private final LiveKitProperties properties;
    private final LiveKitRoomAdminService liveKitRoomAdminService;

    public LiveKitVoiceService(MongoTemplate mongoTemplate, LiveKitProperties properties,
                               LiveKitRoomAdminService liveKitRoomAdminService) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.liveKitRoomAdminService = liveKitRoomAdminService;
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

        Document currentPresence = mongoTemplate.findOne(
                Query.query(Criteria.where("userId").is(user.getId())), Document.class, VOICE_MEMBERS);
        if (currentPresence != null && !roomId.equals(currentPresence.getString("roomId"))) {
            throw new BadRequestException("Leave the current voice room before joining another one");
        }

        long online = mongoTemplate.count(Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS);
        int capacity = room.getInteger("capacity", 10);
        boolean alreadyPresent = currentPresence != null;
        if (!alreadyPresent && online >= capacity) {
            throw new BadRequestException("This voice room is full");
        }

        String livekitRoomName = LiveKitRoomNames.resolve(room);
        if (room.getString("livekitRoomName") == null || room.getString("livekitRoomName").isBlank()) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(roomId)),
                    Update.update("livekitRoomName", livekitRoomName), CHANNELS);
        }
        // LiveKit enforces maxParticipants atomically, even when token requests race across instances.
        liveKitRoomAdminService.ensureRoom(livekitRoomName, capacity);

        boolean serverMuted = mongoTemplate.exists(new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(roomId),
                Criteria.where("userId").is(user.getId()),
                Criteria.where("serverMuted").is(true)
        )), VOICE_MODERATION);

        AccessToken token = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        token.setIdentity(user.getId());
        token.setName(user.getUsername());
        // livekit-server 0.16.0 expects this value in milliseconds.
        token.setTtl(properties.getTokenTtlSeconds() * 1000L);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(livekitRoomName),
                new CanPublish(!serverMuted),
                new CanSubscribe(true),
                new CanPublishData(false)
        );
        if (!serverMuted) {
            token.addGrants(new CanPublishSources(List.of("microphone")));
        }

        return new VoiceJoinResponse(
                properties.getServerUrl(),
                token.toJwt(),
                properties.getTokenTtlSeconds(),
                roomId,
                livekitRoomName
        );
    }

    private void validateConfiguration() {
        properties.validate();
    }

    private boolean canManage(Document room, UserPrincipal user) {
        boolean privileged = user.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_MODERATOR".equals(authority.getAuthority()));
        return privileged || user.getId().equals(room.getString("ownerId"));
    }

}
