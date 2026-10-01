# GameTrust Backend API

Spring Boot backend for the GameTrust gamer matchmaking and social platform. The service exposes authentication, role-based authorization, squad matchmaking, tournaments, clans, reputation, and social APIs. All persistent data is stored in MongoDB.

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

## MongoDB collections

The `gametrust` database contains:

- Security: `users`, `refresh_tokens`
- Squad: `player_profiles`, `matchmaking_sessions`, `player_invites`
- Tournament: `tournaments`, `tournament_brackets`, `tournament_registrations`
- Clan: `clans`, `clan_join_requests`
- Reputation: `reputation_metrics`, `reputation_reports`, `reputation_reviews`, `top_rep_players`
- Social: `social_posts`, `online_players`, `trending_tags`

Unique MongoDB indexes protect `users.username`, `users.email`, and `refresh_tokens.token`. Refresh-token documents also have a TTL index on `expiresAt`.

## Verification performed

- `mvn.cmd test`: 4 authentication service tests pass.
- `mvn.cmd package`: executable JAR builds successfully.
- Live MongoDB checks covered login, `/me`, public feature reads, protected mutations, member rejection from admin API, admin access, refresh rotation, old-token rejection, and logout revocation.
