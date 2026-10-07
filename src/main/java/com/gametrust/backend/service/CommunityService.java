package com.gametrust.backend.service;

import com.gametrust.backend.dto.community.CommunityRequests.CreateRoomRequest;
import com.gametrust.backend.dto.community.CommunityRequests.SendMessageRequest;
import com.gametrust.backend.dto.community.CommunityRequests.UpdateMessageRequest;
import com.gametrust.backend.dto.community.CommunityRequests.UpdateRoomRequest;
import com.gametrust.backend.dto.community.CommunityRequests.UpdateVoiceMemberRequest;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.ResourceNotFoundException;
import com.gametrust.backend.security.UserPrincipal;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommunityService {

    private static final String GAME_HUBS = "game_hubs";
    private static final String CHANNELS = "community_channels";
    private static final String MESSAGES = "channel_messages";
    private static final String VOICE_MEMBERS = "voice_room_members";

    private final MongoTemplate mongoTemplate;
    private final LiveKitRoomAdminService liveKitRoomAdminService;

    public CommunityService(MongoTemplate mongoTemplate, LiveKitRoomAdminService liveKitRoomAdminService) {
        this.mongoTemplate = mongoTemplate;
        this.liveKitRoomAdminService = liveKitRoomAdminService;
    }

    public List<Map<String, Object>> getGames() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "name"));
        List<Map<String, Object>> games = find(query, GAME_HUBS);
        games.forEach(game -> game.put("channels", getChannels(String.valueOf(game.get("id")))));
        return games;
    }

    public Map<String, Object> getGame(String gameId) {
        Map<String, Object> game = publicDocument(findOne(GAME_HUBS, "id", gameId, "Game not found"));
        game.put("channels", getChannels(gameId));
        return game;
    }

    public List<Map<String, Object>> getChannels(String gameId) {
        findOne(GAME_HUBS, "id", gameId, "Game not found");
        Query query = Query.query(Criteria.where("gameId").is(gameId))
                .with(Sort.by(Sort.Direction.ASC, "position"));
        List<Map<String, Object>> channels = find(query, CHANNELS);
        channels.forEach(channel -> {
            if ("VOICE".equals(channel.get("type"))) {
                String roomId = String.valueOf(channel.get("id"));
                channel.put("onlineCount", mongoTemplate.count(
                        Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS));
            }
        });
        return channels;
    }

    public List<Map<String, Object>> getMessages(String channelId, String before, int requestedLimit) {
        Document channel = requireChannel(channelId, "TEXT");
        Query query = Query.query(Criteria.where("channelId").is(channel.getString("id")));
        if (before != null && !before.isBlank()) {
            try {
                query.addCriteria(Criteria.where("createdAt").lt(Instant.parse(before)));
            } catch (DateTimeParseException ex) {
                throw new BadRequestException("before must be a valid ISO-8601 timestamp");
            }
        }
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        List<Map<String, Object>> messages = find(query, MESSAGES);
        Collections.reverse(messages);
        return messages;
    }

    public Map<String, Object> sendMessage(String channelId, SendMessageRequest request, UserPrincipal user) {
        Document channel = requireChannel(channelId, "TEXT");
        Instant now = Instant.now();
        Document message = new Document()
                .append("id", "msg_" + shortId())
                .append("gameId", channel.getString("gameId"))
                .append("channelId", channelId)
                .append("authorId", user.getId())
                .append("authorUsername", user.getUsername())
                .append("content", request.content().trim())
                .append("createdAt", now)
                .append("editedAt", null);
        mongoTemplate.insert(message, MESSAGES);
        return publicDocument(message);
    }

    public Map<String, Object> updateMessage(String messageId, UpdateMessageRequest request, UserPrincipal user) {
        Document message = findOne(MESSAGES, "id", messageId, "Message not found");
        if (!user.getId().equals(message.getString("authorId"))) {
            throw new AccessDeniedException("Only the message author can edit this message");
        }
        Instant editedAt = Instant.now();
        String content = request.content().trim();
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(messageId)),
                new Update().set("content", content).set("editedAt", editedAt), MESSAGES);
        message.put("content", content);
        message.put("editedAt", editedAt);
        return publicDocument(message);
    }

    public void deleteMessage(String messageId, UserPrincipal user) {
        Document message = findOne(MESSAGES, "id", messageId, "Message not found");
        Document channel = findOne(CHANNELS, "id", message.getString("channelId"), "Channel not found");
        boolean isAuthor = user.getId().equals(message.getString("authorId"));
        if (!isAuthor && !canManage(channel, user)) {
            throw new AccessDeniedException("Only the author or room manager can delete this message");
        }
        mongoTemplate.remove(Query.query(Criteria.where("id").is(messageId)), MESSAGES);
    }

    public Map<String, Object> createVoiceRoom(String gameId, CreateRoomRequest request, UserPrincipal user) {
        findOne(GAME_HUBS, "id", gameId, "Game not found");
        int capacity = request.capacity() == null ? 10 : request.capacity();
        long position = mongoTemplate.count(Query.query(Criteria.where("gameId").is(gameId)), CHANNELS) + 1;
        Document room = new Document()
                .append("id", "room_" + shortId())
                .append("gameId", gameId)
                .append("name", request.name().trim())
                .append("slug", slug(request.name()) + "-" + shortId().substring(0, 4))
                .append("type", "VOICE")
                .append("livekitRoomName", "voice_" + gameId + "_" + shortId())
                .append("ownerId", user.getId())
                .append("ownerUsername", user.getUsername())
                .append("capacity", capacity)
                .append("locked", false)
                .append("isDefault", false)
                .append("position", position)
                .append("createdAt", Instant.now());
        mongoTemplate.insert(room, CHANNELS);
        Map<String, Object> result = publicDocument(room);
        result.put("onlineCount", 0);
        return result;
    }

    public Map<String, Object> updateVoiceRoom(String roomId, UpdateRoomRequest request, UserPrincipal user) {
        Document room = requireChannel(roomId, "VOICE");
        requireManager(room, user);
        if (request.name() == null && request.capacity() == null && request.locked() == null) {
            throw new BadRequestException("At least one room field must be provided");
        }
        if (request.capacity() != null) {
            long online = mongoTemplate.count(Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS);
            if (request.capacity() < online) {
                throw new BadRequestException("Capacity cannot be lower than the current online member count");
            }
        }
        Update update = new Update();
        if (request.name() != null) update.set("name", request.name().trim());
        if (request.capacity() != null) update.set("capacity", request.capacity());
        if (request.locked() != null) update.set("locked", request.locked());
        update.set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(roomId)), update, CHANNELS);
        return getRoom(roomId);
    }

    public void deleteVoiceRoom(String roomId, UserPrincipal user) {
        Document room = requireChannel(roomId, "VOICE");
        requireManager(room, user);
        if (Boolean.TRUE.equals(room.getBoolean("isDefault"))) {
            throw new BadRequestException("Default voice rooms cannot be deleted");
        }
        mongoTemplate.remove(Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS);
        mongoTemplate.remove(Query.query(Criteria.where("id").is(roomId)), CHANNELS);
    }

    public Map<String, Object> getRoom(String roomId) {
        Map<String, Object> room = publicDocument(requireChannel(roomId, "VOICE"));
        Query membersQuery = Query.query(Criteria.where("roomId").is(roomId))
                .with(Sort.by(Sort.Direction.ASC, "joinedAt"));
        List<Map<String, Object>> members = find(membersQuery, VOICE_MEMBERS);
        room.put("onlineCount", members.size());
        room.put("members", members);
        return room;
    }

    public Map<String, Object> joinVoiceRoom(String roomId, UserPrincipal user) {
        Document room = requireChannel(roomId, "VOICE");
        if (Boolean.TRUE.equals(room.getBoolean("locked")) && !canManage(room, user)) {
            throw new BadRequestException("This voice room is locked");
        }
        Query currentQuery = Query.query(Criteria.where("userId").is(user.getId()));
        Document current = mongoTemplate.findOne(currentQuery, Document.class, VOICE_MEMBERS);
        if (current != null && roomId.equals(current.getString("roomId"))) {
            return getRoom(roomId);
        }
        int capacity = room.getInteger("capacity", 10);
        long online = mongoTemplate.count(Query.query(Criteria.where("roomId").is(roomId)), VOICE_MEMBERS);
        if (online >= capacity) {
            throw new BadRequestException("This voice room is full");
        }
        mongoTemplate.remove(currentQuery, VOICE_MEMBERS);
        Document member = new Document()
                .append("id", "presence_" + shortId())
                .append("roomId", roomId)
                .append("gameId", room.getString("gameId"))
                .append("userId", user.getId())
                .append("username", user.getUsername())
                .append("muted", false)
                .append("joinedAt", Instant.now());
        try {
            mongoTemplate.insert(member, VOICE_MEMBERS);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("You are already connected to a voice room");
        }
        return getRoom(roomId);
    }

    public void leaveVoiceRoom(String roomId, UserPrincipal user) {
        requireChannel(roomId, "VOICE");
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(roomId),
                Criteria.where("userId").is(user.getId())
        ));
        mongoTemplate.remove(query, VOICE_MEMBERS);
    }

    public Map<String, Object> updateVoiceMember(
            String roomId, String memberUserId, UpdateVoiceMemberRequest request, UserPrincipal user) {
        Document room = requireChannel(roomId, "VOICE");
        requireManager(room, user);
        if (request.muted() == null) {
            throw new BadRequestException("muted is required");
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(roomId),
                Criteria.where("userId").is(memberUserId)
        ));
        Document member = mongoTemplate.findOne(query, Document.class, VOICE_MEMBERS);
        if (member == null) throw new ResourceNotFoundException("Voice member not found");
        if (!request.muted()) {
            throw new BadRequestException("Moderators cannot remotely unmute a participant");
        }
        liveKitRoomAdminService.muteMicrophone(liveKitRoomName(room), memberUserId);
        mongoTemplate.updateFirst(query, Update.update("muted", true), VOICE_MEMBERS);
        member.put("muted", true);
        return publicDocument(member);
    }

    public void kickVoiceMember(String roomId, String memberUserId, UserPrincipal user) {
        Document room = requireChannel(roomId, "VOICE");
        requireManager(room, user);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("roomId").is(roomId),
                Criteria.where("userId").is(memberUserId)
        ));
        if (!mongoTemplate.exists(query, VOICE_MEMBERS)) {
            throw new ResourceNotFoundException("Voice member not found");
        }
        liveKitRoomAdminService.removeParticipant(liveKitRoomName(room), memberUserId);
        mongoTemplate.remove(query, VOICE_MEMBERS);
    }

    private String liveKitRoomName(Document room) {
        String name = room.getString("livekitRoomName");
        return name == null || name.isBlank() ? room.getString("id") : name;
    }

    private Document requireChannel(String channelId, String expectedType) {
        Document channel = findOne(CHANNELS, "id", channelId, "Channel not found");
        if (!expectedType.equals(channel.getString("type"))) {
            throw new BadRequestException("Channel is not a " + expectedType.toLowerCase() + " channel");
        }
        return channel;
    }

    private void requireManager(Document room, UserPrincipal user) {
        if (!canManage(room, user)) {
            throw new AccessDeniedException("Only the room owner, moderator or admin can manage this room");
        }
    }

    private boolean canManage(Document room, UserPrincipal user) {
        boolean privileged = user.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_MODERATOR".equals(authority.getAuthority()));
        return privileged || user.getId().equals(room.getString("ownerId"));
    }

    private Document findOne(String collection, String field, Object value, String error) {
        Document document = mongoTemplate.findOne(Query.query(Criteria.where(field).is(value)), Document.class, collection);
        if (document == null) throw new ResourceNotFoundException(error);
        return document;
    }

    private List<Map<String, Object>> find(Query query, String collection) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document document : mongoTemplate.find(query, Document.class, collection)) {
            result.add(publicDocument(document));
        }
        return result;
    }

    private Map<String, Object> publicDocument(Document document) {
        Map<String, Object> copy = new LinkedHashMap<>(document);
        copy.remove("_id");
        return copy;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String slug(String value) {
        String result = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return result.isBlank() ? "voice-room" : result;
    }
}
