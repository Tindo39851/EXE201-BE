package com.gametrust.backend.config;

import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.repository.UserRepository;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Instant;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(MongoTemplate mongoTemplate, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedPlayers();
        seedTournaments();
        seedClans();
        seedReputation();
        seedSocial();
        seedCommunity();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin", "admin@gametrust.local", passwordEncoder.encode("Admin123!"), Role.ADMIN);
            admin.setReputationScore(99);
            userRepository.save(admin);
        }
        if (!userRepository.existsByUsername("demo")) {
            User demo = new User("demo", "demo@gametrust.local", passwordEncoder.encode("Demo123!"), Role.MEMBER);
            demo.setReputationScore(91);
            userRepository.save(demo);
        }
    }

    private void seedPlayers() {
        insertIfEmpty("player_profiles", List.of(
                d("id", "p1", "initial", "A", "username", "AXIOM_V", "timezone", "PST", "game", "Valorant", "rank", "Radiant", "status", "LFG", "description", "Ex semi-pro IGL looking for 2 more for ranked grind. No excuses, all comms.", "roles", List.of("IGL", "Controller"), "repScore", 9.9, "matches", 842, "winRate", "62%", "accentColor", "red", "micAvailable", true),
                d("id", "p2", "initial", "N", "username", "NULLSHIFT", "timezone", "W", "game", "CS2", "rank", "Global Elite", "status", "LFG", "description", "3k+ hours GE. Looking for a structured 5 stack.", "roles", List.of("AWPer", "Entry"), "repScore", 9.8, "matches", 1264, "winRate", "57%", "accentColor", "green", "micAvailable", true),
                d("id", "p3", "initial", "C", "username", "CR4WLER", "timezone", "EST", "game", "League of Legends", "rank", "Challenger", "status", "LFG", "description", "KR Challenger jungler. ADC duo preferred - must be Diamond+.", "roles", List.of("Jungle", "Mid"), "repScore", 9.7, "matches", 621, "winRate", "71%", "accentColor", "yellow", "micAvailable", true),
                d("id", "p4", "initial", "V", "username", "VECTOR_X", "timezone", "PT", "game", "Apex Legends", "rank", "Predator", "status", "LFG", "description", "Masters to Pred every season. Need a 3rd for ranked splits.", "roles", List.of("Fragger", "IGL"), "repScore", 9.6, "matches", 734, "winRate", "68%", "accentColor", "purple", "micAvailable", true),
                d("id", "p5", "initial", "G", "username", "GHOST_RIG", "timezone", "-", "game", "Overwatch 2", "rank", "Top 500", "status", "LFG", "description", "T500 tank main looking for a chill but focused squad.", "roles", List.of("Tank", "Flex"), "repScore", 9.5, "matches", 536, "winRate", "58%", "accentColor", "orange", "micAvailable", true),
                d("id", "p6", "initial", "K", "username", "KRYPT0N", "timezone", "-", "game", "Valorant", "rank", "Immortal 1", "status", "LFG", "description", "Duelist grinding to Radiant. Need IGL and controller.", "roles", List.of("Duelist", "Flex"), "repScore", 9.4, "matches", 402, "winRate", "54%", "accentColor", "cyan", "micAvailable", true)
        ));
    }

    private void seedTournaments() {
        insertIfEmpty("tournaments", List.of(
                d("id", "t1", "name", "NEON CIRCUIT OPEN", "game", "Valorant", "status", "LIVE", "format", "5v5 Double Elim", "prize", 50000, "countdown", "02:14:27", "teams", 128, "featured", true),
                d("id", "t2", "name", "DARKBYTE INVITATIONAL", "game", "CS2", "status", "OPEN", "format", "5v5 Single Elim", "prize", 25000, "countdown", "09:40:40", "teams", 64, "featured", false),
                d("id", "t3", "name", "PHANTOM LEAGUE S3", "game", "League of Legends", "status", "OPEN", "format", "5v5 Swiss", "prize", 100000, "countdown", "23:05:24", "teams", 256, "featured", false),
                d("id", "t4", "name", "APEX GRID MASTERS", "game", "Apex Legends", "status", "UPCOMING", "format", "3v3 Battle Royale", "prize", 15000, "countdown", "48:00:00", "teams", 60, "featured", true)
        ));
        insertIfEmpty("tournament_brackets", List.of(
                d("tournamentId", "t1", "id", "m1", "round", "Quarterfinals", "team1", d("name", "PHANTOM SYNDICATE", "score", 2, "won", true), "team2", d("name", "NEON WOLVES", "score", 1, "won", false), "status", "COMPLETED"),
                d("tournamentId", "t1", "id", "m2", "round", "Quarterfinals", "team1", d("name", "DARK VECTOR", "score", 2, "won", true), "team2", d("name", "GRID REAPERS", "score", 0, "won", false), "status", "COMPLETED"),
                d("tournamentId", "t1", "id", "m3", "round", "Semifinals", "team1", d("name", "PHANTOM SYNDICATE", "score", "-"), "team2", d("name", "DARK VECTOR", "score", "-"), "status", "LIVE"),
                d("tournamentId", "t1", "id", "m4", "round", "Finals", "team1", d("name", "WINNER MATCH 3", "score", "-"), "team2", d("name", "TBD", "score", "-"), "status", "SCHEDULED")
        ));
    }

    private void seedClans() {
        insertIfEmpty("clans", List.of(
                clan(1, "PHANTOM SYNDICATE", "PSY", 847, "9.842", "ELITE", "cyan", 412, "2019", "Global", "Oldest and most decorated clan on GameTrust.", List.of("Valorant", "CS2", "LoL"), "Trust Score 9.5+"),
                clan(2, "NEON WOLVES", "NW", 634, "9.711", "ELITE", "green", 388, "2020", "NA/EU", "Fierce competitors focused on tactical shooters.", List.of("Valorant", "Apex Legends"), "Trust Score 9.0+"),
                clan(3, "DARK VECTOR", "DV", 512, "9.582", "ALPHA", "purple", 312, "2021", "EU", "High-tier squadron specializing in MOBA strategy.", List.of("LoL", "Dota 2"), "Trust Score 8.5+"),
                clan(4, "GRID REAPERS", "GR", 423, "9.402", "ALPHA", "orange", 295, "2021", "AS", "Aggressive high-tempo tactical FPS clan.", List.of("CS2", "Overwatch 2"), "Trust Score 8.5+"),
                clan(5, "CYBER UNIT 7", "CU7", 308, "9.261", "BETA", "blue", 184, "2022", "SEA", "Cultivating upcoming tier-2 esports talent.", List.of("Valorant", "Rocket League"), "Trust Score 8.0+"),
                clan(6, "VOID PROTOCOL", "VP", 381, "9.134", "BETA", "magenta", 196, "2020", "NA", "Semi-competitive squad for high-ranked grinds.", List.of("Apex Legends", "Fortnite"), "Trust Score 7.5+"),
                clan(7, "NEON SERPENTS", "NS", 278, "8.940", "BETA", "green", 145, "2023", "EU", "New contenders rising through the leaderboard.", List.of("Valorant"), "Trust Score 7.0+"),
                clan(8, "IRON CIRCUIT", "IC", 244, "8.812", "GAMMA", "gray", 98, "2023", "Global", "Open guild for casual and competitive players.", List.of("CS2", "LoL"), "None")
        ));
    }

    private void seedReputation() {
        insertIfEmpty("reputation_metrics", List.of(
                d("id", "platform", "avgRepScore", 9.1, "squadsFormed", 62100, "activeNow", 1203, "totalSessions", 284103, "toxicityRate", 0.4, "successRate", 97.8)
        ));
        insertIfEmpty("reputation_reports", List.of(
                d("id", "#RPT_1041", "type", "TOXICITY", "user", "AXIOM_V5", "status", "TEMP BANNED", "badgeColor", "red"),
                d("id", "#RPT_1040", "type", "AFK", "user", "NULL_FEED", "status", "PENDING", "badgeColor", "yellow"),
                d("id", "#RPT_1039", "type", "CHEATING", "user", "BOT_WAVE1", "status", "CONFIRMED", "badgeColor", "red"),
                d("id", "#RPT_1038", "type", "SMURFING", "user", "SMURF_X18", "status", "MONITOR", "badgeColor", "cyan")
        ));
        insertIfEmpty("reputation_reviews", List.of(
                d("id", "rev_1", "user", "AXIOM_V", "stars", 5, "quote", "Best IGL I've squadded with on this platform.", "author", "NULLSHIFT", "time", "1h ago", "badge", "Small Team", "badgeColor", "cyan"),
                d("id", "rev_2", "user", "CR4WLER", "stars", 5, "quote", "Challenger jungler carrying comms constantly.", "author", "VECTOR_X", "time", "3h ago", "badge", "Clutch Player", "badgeColor", "magenta"),
                d("id", "rev_3", "user", "SYNAPSE", "stars", 4, "quote", "Great rotation awareness and a strong teammate.", "author", "GHOST_RIG", "time", "5h ago", "badge", "Team Player", "badgeColor", "green")
        ));
        insertIfEmpty("top_rep_players", List.of(
                d("rank", 1, "name", "AXIOM_V", "game", "Valorant", "tier", "Radiant", "rep", "9.9", "color", "text-gt-cyan"),
                d("rank", 2, "name", "NULLSHIFT", "game", "CS2", "tier", "Global Elite", "rep", "9.8", "color", "text-gt-green"),
                d("rank", 3, "name", "CR4WLER", "game", "League of Legends", "tier", "Challenger", "rep", "9.7", "color", "text-gt-blue"),
                d("rank", 4, "name", "VECTOR_X", "game", "Apex Legends", "tier", "Predator", "rep", "9.6", "color", "text-gt-purple")
        ));
    }

    private void seedSocial() {
        insertIfEmpty("social_posts", List.of(
                post("post_1", "AXIOM_V", "A", "PST", "LFG", "Valorant", "4m ago", "LFG 2 more for Radiant push. Need a Sentinel and a Duelist.", 112, 17, "from-gt-cyan via-white/50 to-transparent"),
                post("post_2", "NULLSHIFT", "N", "IN", "TOURNAMENT", "CS2", "21m ago", "NEON CIRCUIT OPEN bracket update — winners' semis tonight.", 184, 47, "from-gt-magenta via-white/50 to-transparent"),
                post("post_3", "CR4WLER", "C", "DV1", "TIP", "League of Legends", "28m ago", "Fill out your player card before looking for a squad.", 1842, 178, "from-gt-yellow via-white/50 to-transparent"),
                post("post_4", "GHOST_RIG", "G", "", "ACHIEVEMENT", "Overwatch 2", "5hr ago", "Found a proper 5 stack through GameTrust. Six wins straight.", 86, 71, "from-gt-green via-white/50 to-transparent")
        ));
        insertIfEmpty("online_players", List.of(
                d("username", "AXIOM_V", "initial", "A", "color", "border-gt-red text-gt-red", "tag", "LFG"),
                d("username", "NULLSHIFT", "initial", "N", "color", "border-gt-green text-gt-green", "tag", "LFG"),
                d("username", "VECTOR_X", "initial", "V", "color", "border-gt-purple text-gt-purple", "tag", "LFG"),
                d("username", "GHOST_RIG", "initial", "G", "color", "border-gt-orange text-gt-orange", "tag", "LFT"),
                d("username", "DARK_ECHO", "initial", "D", "color", "border-gt-blue text-gt-blue", "tag", "LFT"),
                d("username", "NEON_JADE", "initial", "N", "color", "border-gt-green text-gt-green", "tag", "LFG")
        ));
        insertIfEmpty("trending_tags", List.of(
                d("tag", "#PhantomSyndicateWin", "count", "83", "color", "text-gt-magenta hover:text-white"),
                d("tag", "#ClarkieMastery", "count", "34", "color", "text-gt-cyan hover:text-white"),
                d("tag", "#NeonCircuitOpen", "count", "81", "color", "text-gt-yellow hover:text-white"),
                d("tag", "#GameTrustSquads", "count", "94", "color", "text-gt-green hover:text-white")
        ));
    }

    private void seedCommunity() {
        String adminId = userRepository.findByUsername("admin")
                .map(User::getId)
                .orElse("system");
        List<Document> games = List.of(
                d("id", "valorant", "name", "Valorant", "shortName", "VAL"),
                d("id", "cs2", "name", "Counter-Strike 2", "shortName", "CS2"),
                d("id", "league-of-legends", "name", "League of Legends", "shortName", "LOL"),
                d("id", "apex-legends", "name", "Apex Legends", "shortName", "APEX"),
                d("id", "lien-quan", "name", "Liên Quân Mobile", "shortName", "AOV"),
                d("id", "free-fire", "name", "Free Fire", "shortName", "FF"),
                d("id", "overwatch-2", "name", "Overwatch 2", "shortName", "OW2"),
                d("id", "fortnite", "name", "Fortnite", "shortName", "FN")
        );

        for (Document game : games) {
            String gameId = game.getString("id");
            if (!mongoTemplate.exists(Query.query(Criteria.where("id").is(gameId)), "game_hubs")) {
                game.append("ownerId", adminId).append("createdAt", Instant.now());
                mongoTemplate.insert(game, "game_hubs");
            }
            seedDefaultChannel(gameId + "_general", gameId, "general", "general", "TEXT", adminId, 1, null);
            seedDefaultChannel(gameId + "_voice_1", gameId, "Squad Room 1", "squad-room-1", "VOICE", adminId, 2, 10);
            seedDefaultChannel(gameId + "_voice_2", gameId, "Squad Room 2", "squad-room-2", "VOICE", adminId, 3, 10);
        }

        mongoTemplate.indexOps("game_hubs").ensureIndex(new Index().on("id", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("community_channels").ensureIndex(new Index().on("id", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("community_channels").ensureIndex(
                new Index().on("gameId", Sort.Direction.ASC).on("slug", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("channel_messages").ensureIndex(new Index().on("id", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("channel_messages").ensureIndex(
                new Index().on("channelId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        mongoTemplate.indexOps("voice_room_members").ensureIndex(new Index().on("id", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("voice_room_members").ensureIndex(new Index().on("userId", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("voice_room_members").ensureIndex(
                new Index().on("roomId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC).unique());
    }

    private void seedDefaultChannel(String id, String gameId, String name, String slug, String type,
                                    String ownerId, int position, Integer capacity) {
        if (mongoTemplate.exists(Query.query(Criteria.where("id").is(id)), "community_channels")) return;
        Document channel = d(
                "id", id,
                "gameId", gameId,
                "name", name,
                "slug", slug,
                "type", type,
                "ownerId", ownerId,
                "ownerUsername", "admin",
                "locked", false,
                "isDefault", true,
                "position", position,
                "createdAt", Instant.now()
        );
        if (capacity != null) channel.append("capacity", capacity);
        mongoTemplate.insert(channel, "community_channels");
    }

    private Document clan(int id, String name, String tag, int members, String rating, String tier, String color,
                          int wins, String founded, String region, String desc, List<String> games, String req) {
        return d("id", id, "name", name, "tag", tag, "members", members, "rating", rating, "tier", tier,
                "color", color, "wins", wins, "founded", founded, "region", region, "desc", desc, "games", games, "req", req);
    }

    private Document post(String id, String username, String initial, String timezone, String tag, String game,
                          String time, String content, int likes, int comments, String gradient) {
        return d("id", id, "username", username, "initial", initial,
                "avatarColor", "bg-gt-cyan text-black border-gt-cyan", "timezone", timezone, "tag", tag,
                "tagColor", "text-gt-cyan border-gt-cyan/50 bg-gt-cyan/10", "game", game, "time", time,
                "content", content, "likes", likes, "liked", false, "comments", comments,
                "bottomGradient", gradient);
    }

    private void insertIfEmpty(String collection, List<Document> documents) {
        if (mongoTemplate.getCollection(collection).countDocuments() == 0) {
            mongoTemplate.getCollection(collection).insertMany(documents);
        }
    }

    private static Document d(Object... values) {
        Document document = new Document();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value instanceof java.util.Map<?, ?> map) {
                value = new Document((java.util.Map<String, Object>) map);
            }
            document.append(String.valueOf(values[index]), value);
        }
        return document;
    }
}
