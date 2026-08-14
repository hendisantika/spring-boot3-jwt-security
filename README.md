# Spring Boot 4 JWT Security

A reference implementation of stateless JWT authentication and authorization with **Spring Boot 4** and
**Spring Security 7**, backed by PostgreSQL.

It covers the full token lifecycle — register, authenticate, refresh, and logout with server-side token
revocation — plus role- and permission-based authorization and an OpenAPI/Swagger UI.

## Features

- Registration and login issuing a signed **HS256** JWT access token + refresh token
- Stateless authentication via a `OncePerRequestFilter` (`JwtAuthenticationFilter`)
- **Server-side token store**: every issued access token is persisted, so tokens can be revoked
- Re-authenticating revokes previously issued tokens for that user
- Logout endpoint that marks the presented token expired + revoked
- Refresh-token endpoint that mints a new access token
- Role-based (`USER`, `ADMIN`, `MANAGER`) and fine-grained permission-based authorization
- Method-level security with `@PreAuthorize`
- BCrypt password hashing
- OpenAPI 3 documentation with a bearer-auth scheme (Swagger UI)

## Tech stack

| Component        | Version              |
|------------------|----------------------|
| Java             | 21+ (built on 21, verified on 25) |
| Spring Boot      | 4.1.0                |
| Spring Security  | 7.1.0                |
| Spring Data JPA  | Hibernate            |
| PostgreSQL       | driver `org.postgresql` |
| JJWT             | 0.13.0               |
| springdoc-openapi| 3.1.0                |
| Lombok           | 1.18.46              |
| Maven            | wrapper 3.9.16       |

## Prerequisites

- JDK 21 or newer
- A running PostgreSQL instance
- Maven (or just use the bundled `./mvnw` wrapper)

## Getting started

### 1. Create the database

The app expects a database named `jwt_security`:

```bash
createdb -h localhost -U postgres jwt_security
```

Tables (`_user`, `token`) are created automatically by Hibernate (`ddl-auto: update`) on first start.

### 2. Configure

`src/main/resources/application.yml` holds the defaults:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/jwt_security
    username: postgres
    password: hendi34
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update              # Hibernate creates/updates _user and token
    show-sql: true                  # log the generated SQL
    open-in-view: false             # no lazy loading outside the service layer
    properties:
      hibernate:
        format_sql: true

application:
  security:
    jwt:
      secret-key: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
      expiration: 86400000          # access token — 1 day
      refresh-token:
        expiration: 604800000       # refresh token — 7 days
```

The Hibernate dialect is deliberately not configured — it is detected from the JDBC URL, and setting it
explicitly only produces a startup warning. `open-in-view` is off because nothing in the app reads a lazy
association during view rendering, so the persistence session does not need to stay open for the whole
request.

Override anything at runtime without editing the file, e.g.:

```bash
java -jar target/spring-boot3-jwt-security-0.0.1-SNAPSHOT.jar \
  --spring.datasource.username=postgres \
  --spring.datasource.password=secret \
  --application.security.jwt.secret-key=<your-base64-hmac-key>
```

> The committed secret key and DB password are sample values for local development. Replace them (and
> supply them via environment variables or a secrets manager) before deploying anywhere real.

### 3. Build and run

```bash
./mvnw clean package          # build + run tests
./mvnw spring-boot:run        # run from source
# or
java -jar target/spring-boot3-jwt-security-0.0.1-SNAPSHOT.jar
```

The app listens on **http://localhost:8080**.

### 4. API documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Click **Authorize** in Swagger UI and paste an access token to call the secured endpoints.

## Endpoints

| Method   | Path                          | Access                                   |
|----------|-------------------------------|------------------------------------------|
| `POST`   | `/api/v1/auth/register`       | public                                   |
| `POST`   | `/api/v1/auth/authenticate`   | public                                   |
| `POST`   | `/api/v1/auth/refresh-token`  | public (send the refresh token as bearer)|
| `POST`   | `/api/v1/auth/logout`         | authenticated                            |
| `GET`    | `/api/v1/demo-controller`     | any authenticated user                   |
| `GET`    | `/api/v1/admin`               | `admin:read`                             |
| `POST`   | `/api/v1/admin`               | `admin:create`                           |
| `PUT`    | `/api/v1/admin`               | `admin:update`                           |
| `DELETE` | `/api/v1/admin`               | `admin:delete`                           |
| `*`      | `/api/v1/management/**`       | reserved for `ADMIN` / `MANAGER` (rules configured in `SecurityConfiguration`; no controller ships with the project) |

Swagger, `/v3/api-docs/**`, and `/webjars/**` are also public; everything else requires authentication.

## Roles and permissions

| Role      | Authorities                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
| `USER`    | `ROLE_USER` only                                                                                   |
| `MANAGER` | `ROLE_MANAGER`, `management:read`, `management:create`, `management:update`, `management:delete`    |
| `ADMIN`   | `ROLE_ADMIN`, all `admin:*` and all `management:*` authorities                                      |

## Example session

```bash
BASE=http://localhost:8080/api/v1

# Register (returns access_token + refresh_token)
curl -X POST $BASE/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstname":"Hendi","lastname":"Santika","email":"admin@yopmail.com","password":"password","role":"ADMIN"}'

# Log in
curl -X POST $BASE/auth/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@yopmail.com","password":"password"}'

TOKEN=<access_token from the response>

# Call a secured endpoint
curl $BASE/demo-controller -H "Authorization: Bearer $TOKEN"
# -> Hello from secured endpoint

curl $BASE/admin -H "Authorization: Bearer $TOKEN"
# -> GET:: admin controller

# Mint a new access token from the refresh token
curl -X POST $BASE/auth/refresh-token -H "Authorization: Bearer <refresh_token>"

# Logout — revokes the presented token
curl -X POST $BASE/auth/logout -H "Authorization: Bearer $TOKEN"
curl -i $BASE/demo-controller -H "Authorization: Bearer $TOKEN"
# -> 403 Forbidden
```

### Responses to bad credentials

| Situation                                                        | Status |
|------------------------------------------------------------------|--------|
| No token, expired, revoked, malformed or tampered-with token      | `403`  |
| Valid token without the required role/authority                   | `403`  |
| Wrong password or unknown user on `/auth/authenticate`            | `403`  |
| Invalid or malformed token on `/auth/refresh-token`               | `401`  |

Invalid tokens are rejected quietly — the parser failure is logged at `DEBUG`, not as an ERROR stack trace,
so an unauthenticated caller cannot flood the logs. Raise the level to see them:

```yaml
logging:
  level:
    com.hendisantika.springboot3jwtsecurity: DEBUG
```

## How it works

```
Request ──► JwtAuthenticationFilter
              │  skips /api/v1/auth/**
              │  reads "Authorization: Bearer <jwt>"
              │  validates signature + expiry (JwtService)
              │  checks the token row is neither expired nor revoked (TokenRepository)
              └► sets the Authentication in the SecurityContext
                   │
                   └► authorization rules (SecurityConfiguration) + @PreAuthorize
```

Each JWT carries a unique `jti` claim, so tokens issued back-to-back for the same user are always distinct
rows in the `token` table.

## Project structure

```
src/main/java/com/hendisantika/springboot3jwtsecurity
├── config
│   ├── ApplicationConfig.java        # UserDetailsService, PasswordEncoder, AuthenticationManager
│   ├── JwtAuthenticationFilter.java  # per-request JWT validation
│   ├── OpenApiConfig.java            # OpenAPI metadata + bearer scheme
│   └── SecurityConfiguration.java    # filter chain and authorization rules
├── controller
│   ├── AdminController.java
│   ├── AuthenticationController.java
│   └── DemoController.java
├── dto                               # request/response payloads
├── entity                            # User, Token, Role, Permission, TokenType
├── repository                        # UserRepository, TokenRepository
└── service
    ├── AuthenticationService.java    # register / authenticate / refresh
    ├── JwtService.java               # token creation and parsing
    └── LogoutService.java            # token revocation on logout
```

## Tests

```bash
./mvnw test
```

The context-load test needs the PostgreSQL database to be reachable.

## Author

**Hendi Santika**
- Email: hendisantika@gmail.com
- Telegram: [@hendisantika34](https://t.me/hendisantika34)
- Link: https://s.id/hendisantika
