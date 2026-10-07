package com.gametrust.backend.service;

import com.gametrust.backend.exception.UnauthorizedException;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveKitWebhookService {

    private static final String CHANNELS = "community_channels";
    private static final String VOICE_MEMBERS = "voice_room_members";
    private static final String VOICE_AUDIT = "voice_session_audit";

    private final MongoTemplate mongoTemplate;
    private final LiveKitWebhookVerifier verifier;

    public LiveKitWebhookService(MongoTemplate mongoTemplate, LiveKitWebhookVerifier verifier) {
        this.mongoTemplate = mongoTemplate;
        this.verifier = verifier;
    }

    public Map<String, Object> receive(String rawBody, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedException("Missing LiveKit webhook signature");
        }
        LivekitWebhook.WebhookEvent event;
        try {
            event = verifier.decode(rawBody, authorizationHeader);
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("Invalid LiveKit webhook signature");
        }
        return processVerifiedEvent(event);
    }

    Map<String, Object> processVerifiedEvent(LivekitWebhook.WebhookEvent event) {
        String eventName = event.getEvent();
        boolean processed = switch (eventName) {
            case "participant_joined" -> handleParticipantJoined(event);
            case "participant_left" -> handleParticipantLeft(event);
            case "room_finished" -> handleRoomFinished(event);
            default -> false;
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", event.getId());
        result.put("event", eventName);
        result.put("processed", processed);
        return result;
    }

    private boolean handleParticipantJoined(LivekitWebhook.WebhookEvent event) {
        if (!event.hasRoom() || !event.hasParticipant()) return false;
        Document room = findChannel(event.getRoom().getName());
        if (room == null) return false;

        LivekitModels.ParticipantInfo participant = event.getParticipant();
        String userId = participant.getIdentity();
        if (userId == null || userId.isBlank()) return false;
        Instant now = Instant.now();

        Query memberQuery = Query.query(Criteria.where("userId").is(userId));
        Update memberUpdate = new Update()
                .setOnInsert("id", "presence_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .set("roomId", room.getString("id"))
                .set("gameId", room.getString("gameId"))
                .set("userId", userId)
                .set("username", displayName(participant))
                .set("livekitParticipantSid", participant.getSid())
                .set("muted", true)
                .set("joinedAt", now);
        mongoTemplate.upsert(memberQuery, memberUpdate, VOICE_MEMBERS);

        Query activeAudit = new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(room.getString("id")),
                Criteria.where("userId").is(userId),
                Criteria.where("leftAt").exists(false)
        ));
        if (!mongoTemplate.exists(activeAudit, VOICE_AUDIT)) {
            mongoTemplate.insert(new Document()
                    .append("id", "voice_session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .append("roomId", room.getString("id"))
                    .append("userId", userId)
                    .append("username", displayName(participant))
                    .append("livekitParticipantSid", participant.getSid())
                    .append("joinedAt", now), VOICE_AUDIT);
        }
        return true;
    }

    private boolean handleParticipantLeft(LivekitWebhook.WebhookEvent event) {
        if (!event.hasRoom() || !event.hasParticipant()) return false;
        Document room = findChannel(event.getRoom().getName());
        if (room == null) return false;
        String userId = event.getParticipant().getIdentity();
        Query roomMember = new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(room.getString("id")),
                Criteria.where("userId").is(userId)
        ));
        mongoTemplate.remove(roomMember, VOICE_MEMBERS);
        mongoTemplate.updateMulti(new Query(new Criteria().andOperator(
                        Criteria.where("roomId").is(room.getString("id")),
                        Criteria.where("userId").is(userId),
                        Criteria.where("leftAt").exists(false)
                )), Update.update("leftAt", Instant.now()), VOICE_AUDIT);
        return true;
    }

    private boolean handleRoomFinished(LivekitWebhook.WebhookEvent event) {
        if (!event.hasRoom()) return false;
        Document room = findChannel(event.getRoom().getName());
        if (room == null) return false;
        String roomId = room.getString("id");
        mongoTemplate.remove(Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS);
        mongoTemplate.updateMulti(new Query(new Criteria().andOperator(
                        Criteria.where("roomId").is(roomId),
                        Criteria.where("leftAt").exists(false)
                )), Update.update("leftAt", Instant.now()), VOICE_AUDIT);
        return true;
    }

    private Document findChannel(String livekitRoomName) {
        return mongoTemplate.findOne(Query.query(Criteria.where("livekitRoomName").is(livekitRoomName)),
                Document.class, CHANNELS);
    }

    private String displayName(LivekitModels.ParticipantInfo participant) {
        return participant.getName().isBlank() ? participant.getIdentity() : participant.getName();
    }
}
