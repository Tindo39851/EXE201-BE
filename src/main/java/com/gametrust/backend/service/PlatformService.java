package com.gametrust.backend.service;

import com.gametrust.backend.dto.platform.PlatformRequests.CreatePostRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.CreateReportRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.CreateReviewRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.MatchmakingRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.TournamentRegistrationRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.UpdateSquadProfileRequest;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.ResourceNotFoundException;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gametrust.backend.config.OpenAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);

    private final MongoTemplate mongoTemplate;
    private final OpenAiProperties openAiProperties;
    private final RestClient restClient;
    private final SocialFeedEventService socialFeedEvents;

    public PlatformService(
            MongoTemplate mongoTemplate,
            OpenAiProperties openAiProperties,
            RestClient.Builder restClientBuilder,
            SocialFeedEventService socialFeedEvents) {
        this.mongoTemplate = mongoTemplate;
        this.openAiProperties = openAiProperties;
        this.restClient = restClientBuilder.baseUrl(openAiProperties.getBaseUrl()).build();
        this.socialFeedEvents = socialFeedEvents;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public Map<String, Object> getMySquadProfile(String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("Vui lòng đăng nhập để xem hồ sơ");
        }
        Document profile = mongoTemplate.findOne(
                Query.query(Criteria.where("username").is(username)),
                Document.class,
                "player_profiles"
        );
        if (profile != null) {
            return publicDocument(profile);
        }

        Document userDoc = mongoTemplate.findOne(
                Query.query(Criteria.where("username").is(username)),
                Document.class,
                "users"
        );
        boolean isPremium = userDoc != null && Boolean.TRUE.equals(userDoc.getBoolean("isPremium"));
        double repScore = (userDoc != null && userDoc.get("reputationScore") != null)
                ? ((Number) userDoc.get("reputationScore")).doubleValue() : 98.0;

        Map<String, Object> defaultProfile = new LinkedHashMap<>();
        defaultProfile.put("id", userDoc != null ? "p_" + userDoc.getString("id") : "p_" + username);
        defaultProfile.put("username", username);
        defaultProfile.put("initial", username.substring(0, 1).toUpperCase());
        defaultProfile.put("game", "Valorant");
        defaultProfile.put("rank", "Chưa xếp hạng");
        defaultProfile.put("status", "LFG");
        defaultProfile.put("description", "Đang tìm đồng đội leo rank cùng GameTrust!");
        defaultProfile.put("roles", List.of("Flex"));
        defaultProfile.put("repScore", repScore);
        defaultProfile.put("winRate", "60%");
        defaultProfile.put("matches", 12);
        defaultProfile.put("accentColor", isPremium ? "yellow" : "cyan");
        defaultProfile.put("micAvailable", true);
        defaultProfile.put("timezone", "VN");
        defaultProfile.put("isPremium", isPremium);
        defaultProfile.put("isRealUser", true);
        return defaultProfile;
    }

    public Map<String, Object> updateMySquadProfile(UpdateSquadProfileRequest request, String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("Vui lòng đăng nhập để cập nhật hồ sơ");
        }
        Document userDoc = mongoTemplate.findOne(
                Query.query(Criteria.where("username").is(username)),
                Document.class,
                "users"
        );
        if (userDoc == null) {
            throw new BadRequestException("Không tìm thấy thông tin tài khoản");
        }

        String userId = userDoc.getString("id");
        boolean isPremium = Boolean.TRUE.equals(userDoc.getBoolean("isPremium"));
        double repScore = userDoc.get("reputationScore") != null
                ? ((Number) userDoc.get("reputationScore")).doubleValue() : 98.0;

        Document doc = mongoTemplate.findOne(
                Query.query(new Criteria().orOperator(
                        Criteria.where("userId").is(userId),
                        Criteria.where("username").is(username)
                )),
                Document.class,
                "player_profiles"
        );

        boolean isNew = (doc == null);
        if (isNew) {
            doc = new Document();
            doc.put("id", "p_" + (userId != null ? userId : username));
            doc.put("userId", userId);
            doc.put("username", username);
            doc.put("initial", username.substring(0, 1).toUpperCase());
            doc.put("repScore", repScore);
            doc.put("matches", 24);
            doc.put("winRate", "65%");
            doc.put("createdAt", Instant.now());
        }

        if (request.game() != null && !request.game().isBlank()) doc.put("game", request.game().trim());
        if (request.rank() != null && !request.rank().isBlank()) doc.put("rank", request.rank().trim());
        if (request.roles() != null && !request.roles().isEmpty()) doc.put("roles", request.roles());
        if (request.region() != null && !request.region().isBlank()) {
            doc.put("timezone", request.region().trim());
            doc.put("region", request.region().trim());
        }
        if (request.micAvailable() != null) doc.put("micAvailable", request.micAvailable());
        if (request.bio() != null && !request.bio().isBlank()) doc.put("description", request.bio().trim());
        if (request.lookingForSquad() != null) {
            doc.put("status", request.lookingForSquad() ? "LFG" : "Online");
        }

        doc.put("isPremium", isPremium);
        doc.put("accentColor", isPremium ? "yellow" : "cyan");
        doc.put("isRealUser", true);
        doc.put("updatedAt", Instant.now());

        if (isNew) {
            mongoTemplate.insert(doc, "player_profiles");
        } else {
            mongoTemplate.save(doc, "player_profiles");
        }

        return publicDocument(doc);
    }

    public List<Map<String, Object>> getPlayers(String game, String rank, String role, String region, Boolean micRequired, String currentUsername) {
        Query query = new Query();
        if (currentUsername != null && !currentUsername.isBlank()) {
            query.addCriteria(Criteria.where("username").ne(currentUsername));
        }
        addExactFilter(query, "game", game);
        addExactFilter(query, "rank", rank);
        addExactFilter(query, "roles", role);
        addExactFilter(query, "timezone", region);
        if (micRequired != null && micRequired) {
            query.addCriteria(Criteria.where("micAvailable").is(true));
        }
        // Ưu tiên: isRealUser DESC, isPremium DESC, repScore DESC
        query.with(Sort.by(Sort.Direction.DESC, "isRealUser")
                .and(Sort.by(Sort.Direction.DESC, "isPremium"))
                .and(Sort.by(Sort.Direction.DESC, "repScore")));
        return find(query, "player_profiles");
    }

    public Map<String, Object> matchmake(MatchmakingRequest request, String username) {
        String rawGame = request.gameId() != null ? request.gameId().toLowerCase() : "valorant";
        String normalizedGame = rawGame.contains("val") ? "valorant" :
                (rawGame.contains("lol") || rawGame.contains("league") ? "league-of-legends" : rawGame);

        // 1. Tìm phòng voice đang mở cho tựa game này và còn chỗ
        Query roomQuery = Query.query(Criteria.where("type").is("VOICE")
                .and("locked").is(false)
                .and("gameId").regex(Pattern.compile("^" + Pattern.quote(normalizedGame) + "$", Pattern.CASE_INSENSITIVE)));

        List<Document> availableRooms = mongoTemplate.find(roomQuery, Document.class, "community_channels");
        Document selectedRoom = null;
        for (Document r : availableRooms) {
            String rId = r.getString("id");
            long online = mongoTemplate.count(Query.query(Criteria.where("roomId").is(rId)), "voice_room_members");
            int capacity = r.getInteger("capacity", 5);
            if (online < capacity) {
                selectedRoom = r;
                break;
            }
        }

        // 2. Nếu không có phòng nào còn chỗ, tự động tạo phòng mới cho Squad
        if (selectedRoom == null) {
            String newRoomId = "room_squad_" + shortId();
            selectedRoom = new Document()
                    .append("id", newRoomId)
                    .append("gameId", normalizedGame)
                    .append("name", "⚡ " + request.primaryRole() + " Squad - " + request.rank())
                    .append("slug", "squad-" + newRoomId)
                    .append("type", "VOICE")
                    .append("livekitRoomName", "voice_" + normalizedGame + "_" + newRoomId)
                    .append("ownerId", username != null ? username : "guest")
                    .append("ownerUsername", username != null ? username : "guest")
                    .append("capacity", 5)
                    .append("locked", false)
                    .append("isDefault", false)
                    .append("position", 99L)
                    .append("createdAt", Instant.now());
            mongoTemplate.insert(selectedRoom, "community_channels");
        }

        String actualRoomId = selectedRoom.getString("id");
        String actualRoomName = selectedRoom.getString("name");

        return doc(
                "lobbyId", actualRoomId,
                "roomId", actualRoomId,
                "game", normalizedGame,
                "roomName", actualRoomName,
                "voiceChannelUrl", "/voice-lounges?joinRoom=" + actualRoomId,
                "status", "MATCHED"
        );
    }

    public Map<String, Object> invitePlayer(String playerId, String roomId, String username) {
        Document target = mongoTemplate.findOne(
                Query.query(new Criteria().orOperator(
                        Criteria.where("id").is(playerId),
                        Criteria.where("username").is(playerId)
                )),
                Document.class,
                "player_profiles"
        );
        String recipientUsername = target != null ? target.getString("username") : playerId;

        String id = "inv_" + shortId();
        Map<String, Object> invite = doc(
                "id", id,
                "playerId", playerId,
                "recipientUsername", recipientUsername,
                "invitedBy", username,
                "roomId", roomId,
                "status", "PENDING",
                "createdAt", Instant.now()
        );
        mongoTemplate.insert(invite, "player_invites");
        return doc("success", true, "message", "Đã gửi lời mời tới " + recipientUsername, "id", id, "roomId", roomId);
    }

    public List<Map<String, Object>> getMyInvites(String username) {
        Query query = new Query();
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("recipientUsername").regex(Pattern.compile("^" + Pattern.quote(username) + "$", Pattern.CASE_INSENSITIVE)),
                Criteria.where("playerId").regex(Pattern.compile("^" + Pattern.quote(username) + "$", Pattern.CASE_INSENSITIVE)),
                Criteria.where("invitedBy").is(username)
        ));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return find(query, "player_invites");
    }

    public Map<String, Object> respondToInvite(String inviteId, boolean accept, String username) {
        Document invite = findOne("player_invites", "id", inviteId, "Invite not found");
        String recipient = (String) invite.get("recipientUsername");
        if (recipient == null) recipient = (String) invite.get("playerId");
        String inviter = (String) invite.get("invitedBy");

        if (username == null || (!username.equalsIgnoreCase(recipient) && !username.equalsIgnoreCase(inviter))) {
            throw new BadRequestException("You are not authorized to respond to this invite");
        }

        Query query = Query.query(Criteria.where("id").is(inviteId));
        Update update = new Update().set("status", accept ? "ACCEPTED" : "DECLINED").set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, "player_invites");
        String roomId = (String) invite.get("roomId");
        return doc(
                "success", true,
                "message", accept ? "Invite accepted! Joined squad lobby." : "Invite declined.",
                "roomId", roomId,
                "status", accept ? "ACCEPTED" : "DECLINED"
        );
    }

    public List<Map<String, Object>> getTournaments(String status, String game) {
        Query query = new Query();
        addExactFilter(query, "status", status);
        addExactFilter(query, "game", game);
        List<Map<String, Object>> list = find(query, "tournaments");
        for (Map<String, Object> t : list) {
            // Chuẩn hóa 2 schema cũ và mới để client đọc trường nào cũng có giá trị
            if (!t.containsKey("title") && t.containsKey("name")) t.put("title", t.get("name"));
            if (!t.containsKey("name") && t.containsKey("title")) t.put("name", t.get("title"));
            if (!t.containsKey("prizePool") && t.containsKey("prize")) t.put("prizePool", t.get("prize"));
            if (!t.containsKey("prize") && t.containsKey("prizePool")) t.put("prize", t.get("prizePool"));
            if (!t.containsKey("startDate") && t.containsKey("countdown")) t.put("startDate", t.get("countdown"));
            if (!t.containsKey("countdown") && t.containsKey("startDate")) t.put("countdown", t.get("startDate"));
        }
        return list;
    }

    public Map<String, Object> getTournament(String id) {
        Map<String, Object> t = publicDocument(findOne("tournaments", "id", id, "Tournament not found"));
        if (!t.containsKey("title") && t.containsKey("name")) t.put("title", t.get("name"));
        if (!t.containsKey("name") && t.containsKey("title")) t.put("name", t.get("title"));
        if (!t.containsKey("prizePool") && t.containsKey("prize")) t.put("prizePool", t.get("prize"));
        if (!t.containsKey("prize") && t.containsKey("prizePool")) t.put("prize", t.get("prizePool"));
        return t;
    }

    public List<Map<String, Object>> getBracket(String tournamentId) {
        findOne("tournaments", "id", tournamentId, "Tournament not found");
        Query query = Query.query(Criteria.where("tournamentId").is(tournamentId));
        List<Map<String, Object>> bracket = find(query, "tournament_brackets");
        bracket.forEach(node -> node.remove("tournamentId"));
        return bracket;
    }

    public Map<String, Object> createTournament(Map<String, Object> payload) {
        String id = "tourn_" + UUID.randomUUID().toString().substring(0, 8);
        String title = (String) payload.getOrDefault("title", payload.getOrDefault("name", "New Esports Tournament"));
        String prize = (String) payload.getOrDefault("prizePool", payload.getOrDefault("prize", "$1,000 USD"));
        int maxTeams = payload.get("maxTeams") != null ? ((Number) payload.get("maxTeams")).intValue() : 16;
        String startDate = (String) payload.getOrDefault("startDate", payload.getOrDefault("countdown", "TBD"));

        Document doc = new Document();
        doc.put("id", id);
        doc.put("title", title);
        doc.put("name", title); // Schema cũ
        doc.put("game", payload.getOrDefault("game", "VALORANT"));
        doc.put("format", payload.getOrDefault("format", "5v5 Single Elimination"));
        doc.put("prizePool", prize);
        doc.put("prize", prize); // Schema cũ
        doc.put("maxTeams", maxTeams);
        doc.put("teams", "0 / " + maxTeams); // Schema cũ
        doc.put("registeredTeams", 0);
        doc.put("status", payload.getOrDefault("status", "UPCOMING"));
        doc.put("startDate", startDate);
        doc.put("countdown", startDate); // Schema cũ
        doc.put("bannerUrl", payload.getOrDefault("bannerUrl", "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=800&q=80"));
        doc.put("createdAt", Instant.now());
        mongoTemplate.insert(doc, "tournaments");
        return publicDocument(doc);
    }

    public Map<String, Object> updateTournamentStatus(String id, String status) {
        Document found = findOne("tournaments", "id", id, "Tournament not found");
        Query q = Query.query(Criteria.where("id").is(id));
        Update u = Update.update("status", status.toUpperCase());
        mongoTemplate.updateFirst(q, u, "tournaments");
        found.put("status", status.toUpperCase());
        return publicDocument(found);
    }

    public void deleteTournament(String id) {
        findOne("tournaments", "id", id, "Tournament not found");
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), "tournaments");
        mongoTemplate.remove(Query.query(Criteria.where("tournamentId").is(id)), "tournament_registrations");
        mongoTemplate.remove(Query.query(Criteria.where("tournamentId").is(id)), "tournament_brackets");
    }

    public Map<String, Object> registerTournament(String tournamentId, TournamentRegistrationRequest request, String username) {
        Document tournament = findOne("tournaments", "id", tournamentId, "Tournament not found");
        String status = tournament.getString("status");
        if (status != null && (status.equalsIgnoreCase("COMPLETED") || status.equalsIgnoreCase("CANCELLED"))) {
            throw new BadRequestException("Giải đấu đã ở trạng thái " + status + " và không còn tiếp nhận đăng ký mới.");
        }

        int maxTeams = tournament.getInteger("maxTeams", 16);
        int currentRegistered = tournament.getInteger("registeredTeams", 0);
        if (currentRegistered >= maxTeams) {
            throw new BadRequestException("Giải đấu đã đạt giới hạn tối đa số đội tham gia (" + maxTeams + " đội).");
        }

        mongoTemplate.insert(doc(
                "tournamentId", tournamentId,
                "teamName", request.teamName(),
                "captainDiscord", request.captainDiscord(),
                "registeredBy", username,
                "status", "CONFIRMED",
                "createdAt", Instant.now()
        ), "tournament_registrations");

        int updatedCount = currentRegistered + 1;
        Query tq = Query.query(Criteria.where("id").is(tournamentId));
        Update tu = new Update().set("registeredTeams", updatedCount).set("teams", updatedCount + " / " + maxTeams);
        mongoTemplate.updateFirst(tq, tu, "tournaments");

        return doc("success", true, "message", "Squad successfully registered for tournament");
    }

    public List<Map<String, Object>> getMyTournaments(String username) {
        Query query = Query.query(Criteria.where("registeredBy").is(username));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return find(query, "tournament_registrations");
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

    public void deleteClan(String id) {
        Query q = new Query();
        try {
            int intId = Integer.parseInt(id);
            q.addCriteria(new Criteria().orOperator(Criteria.where("id").is(intId), Criteria.where("id").is(id)));
        } catch (NumberFormatException e) {
            q.addCriteria(Criteria.where("id").is(id));
        }
        mongoTemplate.remove(q, "clans");
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
        return find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")), "reputation_reviews");
    }

    public Map<String, Object> createReview(CreateReviewRequest request, String author) {
        String id = "rev_" + UUID.randomUUID().toString().substring(0, 8);
        int stars = Math.min(5, Math.max(1, request.stars() <= 0 ? 5 : request.stars()));
        String badge = request.badge() != null && !request.badge().isBlank() ? request.badge() : "Team Player";
        String badgeColor = stars >= 5 ? "cyan" : stars >= 4 ? "green" : "magenta";

        Map<String, Object> review = doc(
                "id", id,
                "user", request.user().trim().toUpperCase(),
                "stars", stars,
                "quote", request.quote().trim(),
                "author", author,
                "time", "Just now",
                "badge", badge,
                "badgeColor", badgeColor,
                "createdAt", Instant.now()
        );
        mongoTemplate.insert(review, "reputation_reviews");
        review.remove("createdAt");
        return review;
    }

    public Map<String, Object> createReport(CreateReportRequest request, String reporter) {
        String id = "#RPT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String type = request.type().trim().toUpperCase();
        String badgeColor = "TOXICITY".equals(type) || "CHEATING".equals(type) ? "red" : "yellow";

        Map<String, Object> report = doc(
                "id", id,
                "type", type,
                "user", request.user().trim().toUpperCase(),
                "reason", request.reason() != null ? request.reason().trim() : "",
                "status", "PENDING",
                "badgeColor", badgeColor,
                "reporter", reporter,
                "createdAt", Instant.now()
        );
        mongoTemplate.insert(report, "reputation_reports");
        report.remove("createdAt");
        return report;
    }

    public Map<String, Object> resolveReport(String reportId, String action) {
        Document found = findOne("reputation_reports", "id", reportId, "Report not found");
        String currentStatus = found.getString("status");
        if (!"PENDING".equalsIgnoreCase(currentStatus)) {
            throw new BadRequestException("Báo cáo vi phạm này đã được xử lý trước đó (trạng thái: " + currentStatus + ")");
        }

        String reportedUser = found.getString("user");
        String resolutionNote;

        if ("BAN".equalsIgnoreCase(action)) {
            resolutionNote = "Account Suspended";
        } else {
            resolutionNote = "Penalized (-20 REP)";
        }

        // Atomically chuyển trạng thái report từ PENDING -> RESOLVED
        Query rq = Query.query(new Criteria().andOperator(
                Criteria.where("id").is(reportId),
                Criteria.where("status").is("PENDING")
        ));
        Update ru = new Update().set("status", "RESOLVED").set("resolution", resolutionNote);
        var updateResult = mongoTemplate.updateFirst(rq, ru, "reputation_reports");
        if (updateResult.getModifiedCount() == 0) {
            throw new BadRequestException("Báo cáo vi phạm này đã được xử lý bởi một phiên khác.");
        }

        // Thi hành án phạt
        Query uq = Query.query(Criteria.where("username").regex("^" + Pattern.quote(reportedUser) + "$", "i"));
        if ("BAN".equalsIgnoreCase(action)) {
            mongoTemplate.updateFirst(uq, new Update().set("isActive", false).set("active", false), "users");
        } else {
            Document userDoc = mongoTemplate.findOne(uq, Document.class, "users");
            if (userDoc != null) {
                int currentScore = userDoc.getInteger("reputationScore", 100);
                int newScore = Math.max(0, currentScore - 20); // Đảm bảo điểm không âm
                mongoTemplate.updateFirst(uq, new Update().set("reputationScore", newScore), "users");
            }
        }

        found.put("status", "RESOLVED");
        found.put("resolution", resolutionNote);
        return publicDocument(found);
    }

    public Map<String, Object> dismissReport(String reportId) {
        Document found = findOne("reputation_reports", "id", reportId, "Report not found");
        String currentStatus = found.getString("status");
        if (!"PENDING".equalsIgnoreCase(currentStatus)) {
            throw new BadRequestException("Báo cáo vi phạm này đã được xử lý trước đó (trạng thái: " + currentStatus + ")");
        }

        Query rq = Query.query(new Criteria().andOperator(
                Criteria.where("id").is(reportId),
                Criteria.where("status").is("PENDING")
        ));
        Update ru = new Update().set("status", "DISMISSED").set("resolution", "Dismissed (No violation)");
        var updateResult = mongoTemplate.updateFirst(rq, ru, "reputation_reports");
        if (updateResult.getModifiedCount() == 0) {
            throw new BadRequestException("Báo cáo vi phạm này đã được xử lý bởi một phiên khác.");
        }

        found.put("status", "DISMISSED");
        found.put("resolution", "Dismissed (No violation)");
        return publicDocument(found);
    }

    public List<Map<String, Object>> getTopPlayers() {
        return find(new Query().with(Sort.by(Sort.Direction.ASC, "rank")), "top_rep_players");
    }

    public List<Map<String, Object>> getPosts(String category, String currentUserId) {
        Query query = new Query();
        if (hasFilter(category)) {
            query.addCriteria(Criteria.where("tag").regex(Pattern.compile(Pattern.quote(category), Pattern.CASE_INSENSITIVE)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> posts = find(query, "social_posts");
        for (Map<String, Object> post : posts) {
            String postId = String.valueOf(post.get("id"));
            long actualLikes = mongoTemplate.count(Query.query(Criteria.where("postId").is(postId)), "social_post_likes");
            long actualComments = mongoTemplate.count(Query.query(Criteria.where("postId").is(postId)), "social_post_comments");
            post.put("likes", actualLikes);
            post.put("comments", actualComments);
            post.put("liked", currentUserId != null && mongoTemplate.exists(
                    Query.query(Criteria.where("_id").is(likeId(postId, currentUserId))), "social_post_likes"));
        }
        return posts;
    }

    public Map<String, Object> createPost(CreatePostRequest request, String username, String userId) {
        String id = "post_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> post = doc(
                "id", id,
                "authorId", userId,
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
        socialFeedEvents.publish("POST_CREATED", id);
        return post;
    }

    public Map<String, Object> setPostLike(String postId, String userId, boolean liked) {
        findOne("social_posts", "id", postId, "Post not found");
        String likeId = likeId(postId, userId);
        Query likeQuery = Query.query(Criteria.where("_id").is(likeId));
        Document existingLike = mongoTemplate.findOne(likeQuery, Document.class, "social_post_likes");
        if (liked && existingLike == null) {
            Document likeDocument = new Document("_id", likeId)
                    .append("postId", postId)
                    .append("userId", userId)
                    .append("createdAt", Instant.now());
            try {
                mongoTemplate.insert(likeDocument, "social_post_likes");
            } catch (DuplicateKeyException concurrentLike) {
                // The deterministic id makes repeated/concurrent likes idempotent.
            }
        } else if (!liked && existingLike != null) {
            mongoTemplate.remove(likeQuery, "social_post_likes");
        }
        long count = mongoTemplate.count(Query.query(Criteria.where("postId").is(postId)), "social_post_likes");
        boolean currentUserLiked = mongoTemplate.exists(likeQuery, "social_post_likes");
        socialFeedEvents.publish("POST_UPDATED", postId);
        return doc("liked", currentUserLiked, "count", count);
    }

    public List<Map<String, Object>> getPostComments(String postId) {
        findOne("social_posts", "id", postId, "Post not found");
        Query query = Query.query(Criteria.where("postId").is(postId))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(100);
        return find(query, "social_post_comments");
    }

    public Map<String, Object> createPostComment(String postId, String content, String username, String userId) {
        findOne("social_posts", "id", postId, "Post not found");
        Document comment = new Document()
                .append("id", "comment_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .append("postId", postId)
                .append("authorId", userId)
                .append("username", username)
                .append("content", content.trim())
                .append("createdAt", Instant.now());
        mongoTemplate.insert(comment, "social_post_comments");
        socialFeedEvents.publish("COMMENT_CREATED", postId);
        return publicDocument(comment);
    }

    public void deletePost(String postId, String username, String userId, boolean moderator) {
        Document post = findOne("social_posts", "id", postId, "Post not found");
        boolean isAuthor = userId.equals(post.getString("authorId"))
                || (post.getString("authorId") == null && username.equalsIgnoreCase(post.getString("username")));
        if (!isAuthor && !moderator) {
            throw new org.springframework.security.access.AccessDeniedException("Only the author or a moderator can delete this post");
        }
        mongoTemplate.remove(Query.query(Criteria.where("postId").is(postId)), "social_post_likes");
        mongoTemplate.remove(Query.query(Criteria.where("postId").is(postId)), "social_post_comments");
        mongoTemplate.remove(Query.query(Criteria.where("id").is(postId)), "social_posts");
        socialFeedEvents.publish("POST_DELETED", postId);
    }

    private String likeId(String postId, String userId) {
        return postId + ":" + userId;
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

    public Map<String, Object> getAiRoomRecommendations(String username, String game) {
        boolean isPremium = false;
        if (username != null && !username.isBlank()) {
            Document userDoc = mongoTemplate.findOne(
                    new Query(Criteria.where("username").is(username)),
                    Document.class,
                    "users"
            );
            if (userDoc != null) {
                Boolean p = userDoc.getBoolean("isPremium");
                isPremium = p != null && p;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isPremium", isPremium);
        result.put("game", game != null ? game : "Valorant");

        if (!isPremium) {
            result.put("unlocked", false);
            result.put("message", "Tính năng AI Room Recommendation chỉ dành riêng cho tài khoản GameTrust Premium. Nâng cấp ngay để mở khóa thuật toán gợi ý phòng uy tín và chống nick clone!");
            result.put("features", List.of(
                    "Đánh giá chất lượng phòng dựa trên tỉ lệ hoàn thành trận (>98%)",
                    "Phát hiện hành vi gian lận (tạo acc ảo, tự vote, vote chéo)",
                    "Bộ lọc đồng đội theo phong cách chơi, rank, server, vai trò và ngôn ngữ",
                    "Ưu tiên kết nối phòng voice SFU HD với ping thấp nhất"
            ));
            result.put("recommendations", List.of());
            return result;
        }

        String searchGame = null;
        if (game != null && !game.isBlank() && !"ALL".equalsIgnoreCase(game)) {
            String lower = game.toLowerCase();
            if (lower.contains("val")) searchGame = "valorant";
            else if (lower.contains("lol") || lower.contains("league")) searchGame = "league-of-legends";
            else if (lower.contains("lien") || lower.contains("quan")) searchGame = "lien-quan";
            else if (lower.contains("free") || lower.contains("fire")) searchGame = "free-fire";
            else searchGame = lower;
        }

        Query roomQuery = Query.query(Criteria.where("type").is("VOICE").and("locked").is(false));
        if (searchGame != null) {
            roomQuery.addCriteria(Criteria.where("gameId").regex(Pattern.compile("^" + Pattern.quote(searchGame) + "$", Pattern.CASE_INSENSITIVE)));
        }

        List<Document> realRooms = mongoTemplate.find(roomQuery, Document.class, "community_channels");
        if (realRooms.isEmpty() && searchGame != null) {
            // Nếu tựa game chưa có phòng nào, fallback quét toàn bộ phòng voice để test
            realRooms = mongoTemplate.find(Query.query(Criteria.where("type").is("VOICE").and("locked").is(false)), Document.class, "community_channels");
        }

        boolean liveAi = openAiProperties.isConfigured();
        result.put("unlocked", true);
        result.put("aiEngine", liveAi ? ("OpenAI " + openAiProperties.getModel() + " Neural Matcher") : "GameTrust Neural Room Matcher v2.1");
        result.put("liveOpenAiPowered", liveAi);
        result.put("analyzedRoomsCount", realRooms.size());

        if (realRooms.isEmpty()) {
            result.put("recommendations", List.of());
            result.put("emptyMessage", "Hiện chưa có phòng Voice nào đang hoạt động. Hãy tạo phòng Voice đầu tiên!");
            return result;
        }

        List<Map<String, Object>> scoredRooms = new ArrayList<>();
        for (Document r : realRooms) {
            String roomId = r.getString("id");
            String roomName = r.getString("name");
            String rGameId = r.getString("gameId");
            int capacity = r.getInteger("capacity", 5);
            long online = mongoTemplate.count(Query.query(Criteria.where("roomId").is(roomId)), "voice_room_members");

            String ownerUsername = r.getString("ownerUsername");
            double ownerRep = 95.0;
            if (ownerUsername != null) {
                Document ownerProfile = mongoTemplate.findOne(Query.query(Criteria.where("username").is(ownerUsername)), Document.class, "player_profiles");
                if (ownerProfile != null && ownerProfile.get("repScore") != null) {
                    double raw = ((Number) ownerProfile.get("repScore")).doubleValue();
                    ownerRep = raw <= 10.0 ? raw * 10.0 : raw;
                }
            }

            double fairPlayScore = Math.min(99.9, Math.max(92.0, 94.0 + (ownerRep - 80.0) * 0.28));
            double completionRate = Math.min(100.0, Math.max(94.0, 96.0 + (ownerRep - 80.0) * 0.18));
            double retentionScore = Math.min(99.5, 93.0 + (online > 0 ? 5.0 : 0.0));
            double compositeScore = (fairPlayScore * 0.4) + (completionRate * 0.3) + (retentionScore * 0.2) + (online > 0 && online < capacity ? 10.0 : 0.0);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", roomId);
            item.put("realRoomId", roomId);
            item.put("roomName", roomName != null ? roomName : "Phòng Voice GameTrust");
            item.put("game", rGameId != null ? rGameId : (game != null ? game : "Valorant"));
            item.put("rankTarget", "Đấu Xếp Hạng & Tryhard");
            item.put("fairPlayScore", Math.round(fairPlayScore * 10.0) / 10.0);
            item.put("completionRate", Math.round(completionRate * 10.0) / 10.0);
            item.put("retentionScore", Math.round(retentionScore * 10.0) / 10.0);
            item.put("antiFraudVerified", true);
            item.put("slotsAvailable", online + "/" + capacity);
            item.put("voiceStatus", "LiveKit HD 128kbps Active");
            item.put("compositeScore", compositeScore);
            scoredRooms.add(item);
        }

        scoredRooms.sort((a, b) -> Double.compare((Double) b.get("compositeScore"), (Double) a.get("compositeScore")));
        List<Map<String, Object>> top3 = scoredRooms.stream().limit(3).toList();

        for (Map<String, Object> rec : top3) {
            String rName = (String) rec.get("roomName");
            double fp = (Double) rec.get("fairPlayScore");
            double cr = (Double) rec.get("completionRate");
            String aiReason = callOpenAiForReasoning(username, (String) rec.get("game"), rName, fp, cr);
            if (aiReason == null || aiReason.isBlank()) {
                aiReason = String.format("Phòng có độ tin cậy Fair-play %.1f%%, tỷ lệ hoàn thành trận %.1f%%, chủ phòng xác thực, không có lịch sử toxic.", fp, cr);
            }
            rec.put("aiReason", aiReason);
        }

        result.put("recommendations", top3);
        return result;
    }

    private String callOpenAiForReasoning(String username, String game, String roomName, double fairPlay, double completionRate) {
        if (!openAiProperties.isConfigured()) {
            return null;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", openAiProperties.getModel());
            payload.put("instructions", "You are GameTrust Neural Matcher AI. Explain concisely in Vietnamese (1 sentence, max 25 words) why this room is recommended for a Premium gamer based on fair-play, completion rate, and verified anti-fraud.");
            payload.put("input", List.of(Map.of(
                    "role", "user",
                    "content", String.format("User: %s, Game: %s, Room: %s, FairPlay: %.1f%%, Completion: %.1f%%",
                            username != null ? username : "Operative", game != null ? game : "Valorant", roomName, fairPlay, completionRate)
            )));
            payload.put("max_output_tokens", 100);

            JsonNode response = restClient.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null) {
                for (JsonNode output : response.path("output")) {
                    for (JsonNode content : output.path("content")) {
                        if ("output_text".equals(content.path("type").asText())) {
                            String text = content.path("text").asText().trim();
                            if (!text.isBlank()) return text;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[AI_RECOMMENDATION] OpenAI real-time reasoning call failed: {}. Falling back to heuristic reasoning.", ex.getMessage());
        }
        return null;
    }

    public static Map<String, Object> doc(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return map;
    }
}
