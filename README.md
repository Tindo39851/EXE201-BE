# GameTrust Backend API

Spring Boot backend for the GameTrust gamer matchmaking and social platform. The service exposes authentication, role-based authorization, Discord-like game communities, squad matchmaking, tournaments, clans, reputation, and social APIs. All persistent data is stored in MongoDB.

## Stack

- Java 17-compatible source (verified on Java 24)
- Spring Boot 3.3.4
- Spring Security 6 with stateless JWT authentication
- Spring Data MongoDB
- JJWT 0.12.6
- Springdoc OpenAPI / Swagger UI
- JUnit 5 and Mockito

## Architecture

```text
Controller -> Service -> MongoRepository / MongoTemplate -> MongoDB
                  |
                  -> JWT + BCrypt security services
```

API responses use this envelope:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {},
  "timestamp": "2026-10-01T00:00:00Z"
}
```

## Run locally

Requirements: Java 17+, Maven 3.9+, and MongoDB on port `27017`.

```powershell
mvn.cmd test
mvn.cmd spring-boot:run
```

If `spring-boot:run` cannot resolve the main class from a Windows path containing Unicode characters, package and run the executable JAR:

```powershell
mvn.cmd package
java -jar .\target\backend-0.0.1-SNAPSHOT.jar
```

- API base URL: `http://localhost:5000/api`
- Swagger UI: `http://localhost:5000/swagger-ui.html`
- OpenAPI JSON: `http://localhost:5000/v3/api-docs`

The first startup seeds demo platform data and two local accounts:

| Role | Username | Password |
|---|---|---|
| MEMBER | `demo` | `Demo123!` |
| ADMIN | `admin` | `Admin123!` |

Change or remove these development credentials before deployment.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/gametrust` | MongoDB connection and database |
| `JWT_SECRET` | local development key | HMAC signing key; replace in production |
| `JWT_EXPIRATION` | `900000` | Access-token lifetime in milliseconds |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh-token lifetime in milliseconds |

## Authentication and authorization

Access tokens are sent as `Authorization: Bearer <accessToken>`. Each access and refresh token has a random JWT ID (`jti`) so token rotation cannot generate duplicate MongoDB keys.

- Registration creates a `MEMBER`, BCrypt-hashes the password, and returns an access/refresh pair.
- Login accepts either username or email.
- Refresh performs rotation: the supplied token is revoked and a new access/refresh pair is returned.
- Logout revokes the supplied refresh token.
- Protected feature mutations require any authenticated role.
- `/api/admin/**` requires `ROLE_ADMIN`; a member receives `403`.
- Missing/invalid authentication on protected routes receives `401`.

### Authentication APIs

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create member and return tokens + profile |
| POST | `/api/auth/login` | Public | Authenticate username/email and password |
| POST | `/api/auth/refresh-token` | Public | Rotate a valid refresh token |
| POST | `/api/auth/refresh` | Public | Alias of refresh-token |
| POST | `/api/auth/logout` | Public | Revoke the supplied refresh token |
| GET | `/api/auth/me` | Bearer token | Return the current user profile |
| GET | `/api/admin/users` | ADMIN | List user profiles; verifies role authorization |

Example login:

```json
POST /api/auth/login
{
  "emailOrUsername": "demo",
  "password": "Demo123!"
}
```

## Feature APIs

### Squad Finder

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/squads/players` | Public | List players; optional `game`, `rank`, `role`, `region`, `micRequired` filters |
| POST | `/api/squads/matchmake` | Authenticated | Create and persist a matched lobby |
| POST | `/api/squads/invite/{playerId}` | Authenticated | Persist a pending player invitation |

Matchmaking request:

```json
{
  "gameId": "VAL",
  "primaryRole": "Duelist",
  "rank": "Diamond",
  "region": "SEA",
  "neededRoles": ["Controller", "Sentinel"],
  "micRequired": true
}
```

### Tournaments

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/tournaments` | Public | List tournaments; optional `status` and `game` filters |
| GET | `/api/tournaments/{id}` | Public | Get tournament details |
| GET | `/api/tournaments/{id}/bracket` | Public | Get bracket nodes |
| POST | `/api/tournaments/{id}/register` | Authenticated | Persist team registration with team name and Discord captain |

Registration body:

```json
{
  "teamName": "CYBER PROTOCOL ELITE",
  "captainDiscord": "Captain#1337"
}
```

### Clans

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/clans` | Public | List clans; optional `tier` and `region` filters |
| GET | `/api/clans/{id}` | Public | Get one clan |
| POST | `/api/clans/{id}/join-request` | Authenticated | Persist a pending membership request |

### Reputation

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/reputation/metrics` | Public | Platform reputation and health metrics |
| GET | `/api/reputation/reports` | Public | Moderation report feed used by the current UI |
| GET | `/api/reputation/reviews` | Public | Player reviews |
| GET | `/api/reputation/top-players` | Public | Reputation leaderboard |

### Social network

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/social/feed` | Public | List posts; optional `category` filter |
| POST | `/api/social/posts` | Authenticated | Persist a post attributed to the JWT user |
| POST | `/api/social/posts/{id}/like` | Authenticated | Toggle like and persist the count |
| GET | `/api/social/online-players` | Public | List currently advertised online players |
| GET | `/api/social/trending-tags` | Public | List trending tags |

### Game communities, chat and voice rooms

The first startup creates 8 game hubs. Every game has one `#general` text channel and two default voice rooms (`Squad Room 1`, `Squad Room 2`). Default rooms can be renamed, resized and locked but cannot be deleted. A user can be connected to only one voice room at a time.

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/community/games` | Public | List game hubs and their channels |
| GET | `/api/community/games/{gameId}` | Public | Get one game hub and channels |
| GET | `/api/community/games/{gameId}/channels` | Public | List the game's text and voice channels |
| GET | `/api/community/channels/{channelId}/messages?before=&limit=50` | Public | Read up to 100 messages; `before` is an ISO timestamp |
| POST | `/api/community/channels/{channelId}/messages` | Authenticated | Send a message to a text channel |
| PATCH | `/api/community/messages/{messageId}` | Message author | Edit a message |
| DELETE | `/api/community/messages/{messageId}` | Author/owner/mod/admin | Delete a message |
| GET | `/api/community/rooms/{roomId}` | Public | Read room state and online members |
| POST | `/api/community/rooms/{roomId}/join` | Authenticated | Join/switch to a voice room |
| POST | `/api/community/rooms/{roomId}/leave` | Authenticated | Leave a voice room |
| POST | `/api/community/games/{gameId}/rooms` | Authenticated | Create an extra voice room; caller becomes owner |
| PATCH | `/api/community/rooms/{roomId}` | Owner/mod/admin | Rename, resize or lock a room |
| DELETE | `/api/community/rooms/{roomId}` | Owner/mod/admin | Delete a non-default room |
| PATCH | `/api/community/rooms/{roomId}/members/{userId}` | Owner/mod/admin | Mute/unmute a room member |
| DELETE | `/api/community/rooms/{roomId}/members/{userId}` | Owner/mod/admin | Kick a room member |

Message body:

```json
{ "content": "Tìm thêm 1 support đánh rank tối nay" }
```

Create room body:

```json
{ "name": "Ranked Team A", "capacity": 5 }
```

Room state and chat history are persisted by this REST API. Real-time delivery and actual voice media should be connected later through WebSocket plus a WebRTC provider such as LiveKit; audio is not transported through REST.

## MongoDB collections

The `gametrust` database contains:

- Security: `users`, `refresh_tokens`
- Squad: `player_profiles`, `matchmaking_sessions`, `player_invites`
- Tournament: `tournaments`, `tournament_brackets`, `tournament_registrations`
- Clan: `clans`, `clan_join_requests`
- Reputation: `reputation_metrics`, `reputation_reports`, `reputation_reviews`, `top_rep_players`
- Social: `social_posts`, `online_players`, `trending_tags`
- Communities: `game_hubs`, `community_channels`, `channel_messages`, `voice_room_members`

Unique MongoDB indexes protect `users.username`, `users.email`, and `refresh_tokens.token`. Refresh-token documents also have a TTL index on `expiresAt`.

## Verification performed

- `mvn.cmd test`: 6 authentication/JWT tests pass.
- `mvn.cmd package`: executable JAR builds successfully.
- Live MongoDB checks covered all 8 seeded game hubs, one text + two voice channels per game, chat persistence, join/leave presence, admin mute, owner room updates/deletion and `403` rejection when a member tries to manage a default room.
- Authentication checks covered login, `/me`, protected mutations, member rejection from admin APIs, refresh rotation, old-token rejection, and logout revocation.
