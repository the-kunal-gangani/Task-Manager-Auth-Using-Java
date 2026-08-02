# Task Manager API

A Spring Boot REST API built to learn authentication and authorization from the ground up — Spring Security + JWT, with a simple `USER`/`ADMIN` role split and per-user data ownership (you can only see and modify your own tasks).

This project deliberately fills a specific gap: a prior project (Library Management System) covered relational data modeling (one-to-many, many-to-many) but had **no auth at all** — anyone could hit any endpoint. This project exists to close that gap.

## What This Demonstrates

- **Stateless JWT authentication** — no server-side sessions; every request proves who it is independently via a signed token
- **Password hashing with BCrypt** — passwords are never stored or compared in plain text
- **A custom `UserDetailsService`** — bridges Spring Security's generic auth machinery to a real Postgres `users` table
- **A custom `OncePerRequestFilter`** — validates the JWT on every incoming request, before it ever reaches a controller
- **Method-level authorization** with `@PreAuthorize("hasRole('ADMIN')")` — some endpoints are gated by role
- **Resource ownership enforcement** — a *data-dependent* authorization rule (do you own this specific row?) that lives in the service layer, since it can't be expressed as a simple annotation
- **Correct 401 vs 403 semantics** — `401` means "I don't know who you are" (no/invalid/expired token), `403` means "I know exactly who you are, and the answer is no" (wrong owner, wrong role) — a distinction many real APIs actually get backwards

## Tech Stack

- Java 17
- Spring Boot 3.3 (Web, Data JPA, Security, Validation)
- PostgreSQL
- JJWT 0.12.6 (JWT creation/verification)
- Lombok

## Prerequisites

- JDK 17+
- Maven 3.8+
- PostgreSQL running locally

## Setup

1. Create the database (in `psql`):
```sql
   CREATE DATABASE task_manager_db;
```
2. Check `src/main/resources/application.properties` — update the datasource username/password if yours differ from `postgres`/`library123`.
3. Run it:
```bash
   mvn spring-boot:run
```
   Tables (`users`, `tasks`) are auto-created on first run via `spring.jpa.hibernate.ddl-auto=update`.

The API runs on **`http://localhost:8081`** — intentionally not `8080`, so it can run alongside other local Spring Boot projects (like the Library Management System) without a port collision.

## How the Auth Flow Actually Works

1. **`POST /api/auth/register`** — creates a user (always with role `USER` — there's no way to self-assign `ADMIN` through the API), hashes the password with BCrypt, and returns a JWT immediately (no separate login step needed right after signup).
2. **`POST /api/auth/login`** — Spring Security's `AuthenticationManager` verifies the submitted password against the stored hash, and a JWT is issued on success.
3. **Every protected request** must include:

## Authorization: Bearer <token>

A custom filter (`JwtAuthenticationFilter`) intercepts every request, validates the token's signature and expiry, and — if valid — tells Spring Security who's making the request for the rest of that request's lifecycle.
4. **Tokens expire after 1 hour** (`app.jwt.expiration-ms` in `application.properties`). After that, the user has to log in again for a fresh one — there's no refresh-token mechanism in this version (a natural next step if you wanted to extend this project).

### Why JWT is "stateless"

Traditional session-based auth stores session data server-side and gives the browser a cookie pointing at it. This app does neither — `SecurityConfig` explicitly sets `SessionCreationPolicy.STATELESS`. All the information needed to verify a request (who you are, when the token expires) is packed into the token itself, signed so it can't be tampered with. The server holds nothing between requests — which is part of why JWT-based APIs scale well without sticky-session concerns.

## API Reference

### Auth — public, no token required

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | `{"username", "email", "password"}` | Returns `201`, `password` must be 8+ chars |
| POST | `/api/auth/login` | `{"username", "password"}` | Returns `200` |

Both return: `{"token", "username", "role"}`

### Tasks — protected, requires `Authorization: Bearer <token>`

| Method | Path | Access | Body |
|---|---|---|---|
| POST | `/api/tasks` | any authenticated user | `{"title", "description"}` |
| GET | `/api/tasks` | your own tasks only | — |
| GET | `/api/tasks/all` | **ADMIN only** | — |
| GET | `/api/tasks/{id}` | owner or admin | — |
| PUT | `/api/tasks/{id}` | owner or admin | `{"title", "description"}` |
| POST | `/api/tasks/{id}/complete` | owner or admin | — |
| DELETE | `/api/tasks/{id}` | owner or admin | — |

A non-owner, non-admin user hitting any owner-scoped endpoint gets `403 Forbidden` — proven out during testing with two separate registered users.

## Error Responses

Every error returns the same consistent JSON shape, via a global exception handler:
```json
{
  "timestamp": "...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```

| Status | When it happens |
|---|---|
| `401` | No token, invalid token, expired token, or wrong login credentials |
| `403` | Valid token, but wrong owner of a task, or missing `ADMIN` role |
| `404` | Task doesn't exist |
| `409` | Username or email already taken at registration |
| `400` | Request body failed validation (response includes a `fieldErrors` map) |

Note on `401` for bad login specifically: the message is always the generic `"Invalid username or password"`, never anything more specific like "username not found" — this is deliberate, so the API never lets someone enumerate valid usernames by trial and error.

## Project Structure

| Package | Contents | Purpose |
|---|---|---|
| `entity/` | `User` (implements `UserDetails`), `Role` (enum), `Task` | Database-mapped classes |
| `repository/` | `UserRepository`, `TaskRepository` | Spring Data JPA — CRUD + derived queries (`findByOwnerId`, `existsByUsername`, etc.) |
| `security/` | `JwtService`, `CustomUserDetailsService`, `JwtAuthenticationFilter`, `SecurityConfig` | All the actual auth mechanics — kept separate from business logic on purpose |
| `dto/` | `AuthDtos`, `TaskDtos` (Java records) | Request/response shapes — entities never leave the service layer |
| `service/` | `AuthService` (register/login), `TaskService` (CRUD + ownership checks) | Business logic |
| `controller/` | `AuthController`, `TaskController` | Thin HTTP layer — no logic, just routes to services |
| `exception/` | Custom exceptions + `GlobalExceptionHandler` | Centralized error → HTTP status mapping |

### Why `security/` is a separate package from `service/`

`JwtService` doesn't know or care about tasks or users as a domain concept — it only knows about tokens. Keeping it separate means if this project ever swapped JWT for session-based auth or OAuth, only this one package would need to change; `TaskService` and `TaskController` wouldn't need to know or care.

## Testing It End-to-End (PowerShell)

```powershell
# 1. Register a user
Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body '{"username": "kunal", "email": "kunal@test.com", "password": "password123"}'

# 2. Log in and save the token for reuse
$token = (Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username": "kunal", "password": "password123"}').token

# 3. Create a task
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks" -Method POST -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body '{"title": "Learn Spring Security", "description": "JWT + ownership checks"}'

# 4. List your own tasks
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks" -Headers @{Authorization="Bearer $token"}

# 5. Confirm it's actually protected (should fail with 401)
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks"
```

### Testing the ownership boundary

```powershell
# Register a second user
$token2 = (Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body '{"username": "priya", "email": "priya@test.com", "password": "password123"}').token

# Try to access kunal's task as priya — should fail with 403
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks/1" -Headers @{Authorization="Bearer $token2"}
```

### Testing the admin-only endpoint

```powershell
# As a regular user — should fail with 403
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks/all" -Headers @{Authorization="Bearer $token"}
```

Then, in `psql`, promote a user manually (there's no self-promotion endpoint — see Notes below):
```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'kunal';
```

Get a **fresh** token (the old one has the old role baked into it and won't reflect the change):
```powershell
$token = (Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username": "kunal", "password": "password123"}').token

# Retry — should now succeed and return every task from every user
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks/all" -Headers @{Authorization="Bearer $token"}
```

## Notes & Known Limitations

- **No self-promotion to `ADMIN`** — this is by design, not an oversight. `RegisterRequest` deliberately has no `role` field, so there's no path for a client to declare themselves an admin. Promoting a user requires direct database access.
- **No refresh tokens** — once a token expires (1 hour by default), the user must log in again. A refresh-token flow would be a natural extension.
- **JWT secret is a placeholder** (`app.jwt.secret` in `application.properties`) — fine for local learning, but a real secret should never be committed to source control; it'd typically come from an environment variable or secrets manager instead.
- **No automated tests yet** — a good next step would be JUnit + Mockito tests around `TaskService`'s ownership logic specifically, since that's the most behaviorally interesting code in the project.