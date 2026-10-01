package com.gametrust.backend.service;

import com.gametrust.backend.dto.platform.PlatformRequests.CreatePostRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.MatchmakingRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.TournamentRegistrationRequest;
import com.gametrust.backend.exception.ResourceNotFoundException;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PlatformService {

    private final MongoTemplate mongoTemplate;

    public PlatformService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Map<String, Object>> getPlayers(String game, String rank, String role, String region, Boolean micRequired) {
        Query query = new Query();
        addExactFilter(query, "game", game);
        addExactFilter(query, "rank", rank);
        addExactFilter(query, "roles", role);
        addExactFilter(query, "timezone", region);
        if (micRequired != null && micRequired) {
            query.addCriteria(Criteria.where("micAvailable").is(true));
        }
        query.with(Sort.by(Sort.Direction.DESC, "repScore"));
        return find(query, "player_profiles");
    }

    public Map<String, Object> matchmake(MatchmakingRequest request, String username) {
        String lobbyId = "lobby_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> result = doc(
                "lobbyId", lobbyId,
                "game", request.gameId(),
                "channel", "ranked-solo-duo",
                "matchedCount", Math.min(3, request.neededRoles().size() + 1),
                "maxPlayers", 5,
                "voiceChannelUrl", "https://discord.gg/gametrust-" + lobbyId,
                "status", "MATCHED"
        );
        Map<String, Object> session = new LinkedHashMap<>(result);
        session.put("username", username);
        session.put("primaryRole", request.primaryRole());
        session.put("rank", request.rank());
        session.put("region", request.region());
        session.put("neededRoles", request.neededRoles());
        session.put("micRequired", request.micRequired());
        session.put("createdAt", Instant.now());
        mongoTemplate.insert(session, "matchmaking_sessions");
        return result;
    }

    public Map<String, Object> invitePlayer(String playerId, String username) {
        findOne("player_profiles", "id", playerId, "Player not found");
        mongoTemplate.insert(doc(
                "playerId", playerId,
                "invitedBy", username,
                "status", "PENDING",
                "createdAt", Instant.now()
        ), "player_invites");
        return doc("success", true, "message", "Invite dispatched to " + playerId);
    }

    public List<Map<String, Object>> getTournaments(String status, String game) {
        Query query = new Query();
        addExactFilter(query, "status", status);
        addExactFilter(query, "game", game);
        return find(query, "tournaments");
    }

    public Map<String, Object> getTournament(String id) {
        return publicDocument(findOne("tournaments", "id", id, "Tournament not found"));
    }

    public List<Map<String, Object>> getBracket(String tournamentId) {
        findOne("tournaments", "id", tournamentId, "Tournament not found");
        Query query = Query.query(Criteria.where("tournamentId").is(tournamentId));
        List<Map<String, Object>> bracket = find(query, "tournament_brackets");
        bracket.forEach(node -> node.remove("tournamentId"));
        return bracket;
    }

    public Map<String, Object> registerTournament(String tournamentId, TournamentRegistrationRequest request, String username) {
        findOne("tournaments", "id", tournamentId, "Tournament not found");
        mongoTemplate.insert(doc(
                "tournamentId", tournamentId,
                "teamName", request.teamName(),
                "captainDiscord", request.captainDiscord(),
                "registeredBy", username,
                "status", "CONFIRMED",
                "createdAt", Instant.now()
        ), "tournament_registrations");
        return doc("success", true, "message", "Squad successfully registered for tournament");
    }

    public List<Map<String, Object>> getClans(String tier, String region) {
        Query query = new Query();
        addExactFilter(query, "tier", tier);
        addExactFilter(query, "region", region);
        query.with(Sort.by(Sort.Direction.DESC, "rating"));
        return find(query, "clans");
    }

    public Map<String, Object> getClan(int id) {
        return publicDocument(findOne("clans", "id", id, "Clan not found"));
    }

    public Map<String, Object> requestJoinClan(int clanId, String username) {
        findOne("clans", "id", clanId, "Clan not found");
        mongoTemplate.insert(doc(
                "clanId", clanId,
                "username", username,
                "status", "PENDING",
                "createdAt", Instant.now()
        ), "clan_join_requests");
        return doc("success", true, "message", "Membership application submitted successfully");
    }

    public Map<String, Object> getMetrics() {
        return publicDocument(findOne("reputation_metrics", "id", "platform", "Metrics not found"));
    }

    public List<Map<String, Object>> getReports() {
        return find(new Query(), "reputation_reports");
    }

    public List<Map<String, Object>> getReviews() {
        return find(new Query(), "reputation_reviews");
    }

    public List<Map<String, Object>> getTopPlayers() {
        return find(new Query().with(Sort.by(Sort.Direction.ASC, "rank")), "top_rep_players");
    }

    public List<Map<String, Object>> getPosts(String category) {
        Query query = new Query();
        if (hasFilter(category)) {
            query.addCriteria(Criteria.where("tag").regex(Pattern.compile(Pattern.quote(category), Pattern.CASE_INSENSITIVE)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return find(query, "social_posts");
    }

    public Map<String, Object> createPost(CreatePostRequest request, String username) {
        String id = "post_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> post = doc(
                "id", id,
                "username", username,
                "initial", username.substring(0, 1).toUpperCase(),
                "avatarColor", "bg-gt-cyan text-black border-gt-cyan",
                "timezone", "NOW",
                "tag", request.tag() == null || request.tag().isBlank() ? "LFG" : request.tag().toUpperCase(),
                "tagColor", "text-gt-cyan border-gt-cyan/50 bg-gt-cyan/10",
                "game", request.game() == null || request.game().isBlank() ? "Active" : request.game(),
                "time", "Just now",
                "content", request.content().trim(),
                "likes", 0,
                "liked", false,
                "comments", 0,
                "bottomGradient", "from-gt-cyan to-gt-magenta",
                "createdAt", Instant.now()
        );
        mongoTemplate.insert(post, "social_posts");
        post.remove("createdAt");
        return post;
    }

    public Map<String, Object> toggleLike(String postId) {
        Document post = findOne("social_posts", "id", postId, "Post not found");
        boolean liked = Boolean.TRUE.equals(post.getBoolean("liked"));
        int likes = post.getInteger("likes", 0) + (liked ? -1 : 1);
        likes = Math.max(0, likes);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(postId)),
                new Update().set("liked", !liked).set("likes", likes),
                "social_posts"
        );
        return doc("liked", !liked, "count", likes);
    }

    public List<Map<String, Object>> getOnlinePlayers() {
        return find(new Query(), "online_players");
    }

    public List<Map<String, Object>> getTrendingTags() {
        return find(new Query(), "trending_tags");
    }

    private void addExactFilter(Query query, String field, String value) {
        if (hasFilter(value)) {
            query.addCriteria(Criteria.where(field).regex(Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE)));
        }
    }

    private boolean hasFilter(String value) {
        return value != null && !value.isBlank() && !"ALL".equalsIgnoreCase(value);
    }

    private List<Map<String, Object>> find(Query query, String collection) {
        List<Document> documents = mongoTemplate.find(query, Document.class, collection);
        List<Map<String, Object>> result = new ArrayList<>(documents.size());
        for (Document document : documents) {
            result.add(publicDocument(document));
        }
        return result;
    }

    private Document findOne(String collection, String field, Object value, String message) {
        Document document = mongoTemplate.findOne(
                Query.query(Criteria.where(field).is(value)),
                Document.class,
                collection
        );
        if (document == null) {
            throw new ResourceNotFoundException(message);
        }
        return document;
    }

    private Map<String, Object> publicDocument(Document document) {
        Map<String, Object> result = new LinkedHashMap<>(document);
        result.remove("_id");
        result.remove("createdAt");
        return result;
    }

    public static Map<String, Object> doc(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return map;
    }
}
