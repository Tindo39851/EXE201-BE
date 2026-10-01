# GameTrust Backend — Authentication & Authorization Service

Backend REST API for **GameTrust** (Esports Matchmaking & Gamer Social Network Platform), built with **Spring Boot 3.3.4**, **Spring Security 6**, and **JJWT 0.12.6** implementing a strict **3-Layer Architecture**.

---

## 🏛️ 3-Layer Architecture

```text
Controller (HTTP endpoints, validation, response format)
    ↓
Service (Business logic, JWT generation, password encryption)
    ↓
Repository (Spring Data JPA, database queries)
    ↓
Database (PostgreSQL / H2)
```

### Directory Structure

```text
D:\Code\EXE\BE\
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/gametrust/backend/
    │   │   ├── GameTrustApplication.java
    │   │   ├── config/
    │   │   │   └── OpenApiConfig.java
    │   │   ├── controller/
    │   │   │   └── AuthController.java
    │   │   ├── dto/
    │   │   │   ├── auth/
    │   │   │   │   ├── AuthResponse.java
    │   │   │   │   ├── LoginRequest.java
    │   │   │   │   ├── RefreshTokenRequest.java
    │   │   │   │   ├── RegisterRequest.java
    │   │   │   │   └── UserResponse.java
    │   │   │   └── common/
    │   │   │       ├── ApiResponse.java
    │   │   │       └── ErrorResponse.java
    │   │   ├── entity/
    │   │   │   ├── RefreshToken.java
    │   │   │   ├── Role.java (MEMBER, MODERATOR, ADMIN)
    │   │   │   └── User.java
    │   │   ├── exception/
    │   │   │   ├── AppException.java
    │   │   │   ├── BadRequestException.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   └── UnauthorizedException.java
    │   │   ├── repository/
    │   │   │   ├── RefreshTokenRepository.java
    │   │   │   └── UserRepository.java
    │   │   ├── security/
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtService.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── UserDetailsServiceImpl.java
    │   │   │   └── UserPrincipal.java
    │   │   └── service/
    │   │       ├── AuthService.java
    │   │       └── impl/
    │   │           └── AuthServiceImpl.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/gametrust/backend/
            └── service/
                └── AuthServiceTest.java
```

---

## 🚀 API Endpoints

Base URL: `http://localhost:5000`

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register new gamer account (default role: `MEMBER`) | No |
| `POST` | `/api/auth/login` | Log in with username/email & password | No |
| `POST` | `/api/auth/refresh-token` | Rotate refresh token and get a new access token | No |
| `POST` | `/api/auth/logout` | Revoke refresh token | No |
| `GET` | `/api/auth/me` | Get currently authenticated gamer profile | Yes (Bearer JWT) |

### 📖 Swagger OpenAPI Documentation
Once the server is running, explore interactive Swagger UI at:
👉 **`http://localhost:5000/swagger-ui.html`**

---

## ⚙️ Configuration & Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `5000` | HTTP Server port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/gametrust` | PostgreSQL JDBC connection URL |
| `DATABASE_USERNAME` | `postgres` | Database username |
| `DATABASE_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | `404E63526655...` | 256-bit secret key for HMAC SHA |
| `JWT_EXPIRATION` | `900000` (15 mins) | Access token validity in ms |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 days) | Refresh token validity in ms |

---

## 🛠️ How to Run

### In IntelliJ IDEA / Eclipse / VS Code:
1. Open folder `D:\Code\EXE\BE` as a Maven project.
2. Select Java 17 or higher SDK.
3. Run `GameTrustApplication.java`.

### In Terminal (with Maven installed):
```bash
mvn clean test
mvn spring-boot:run
```
