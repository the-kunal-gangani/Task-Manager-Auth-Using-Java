# Task Manager API

A Spring Boot REST API demonstrating authentication and authorization with Spring Security + JWT — users register, log in, and manage their own tasks, with a simple USER/ADMIN role split.

## What This Demonstrates

- Stateless JWT authentication (no server-side sessions)
- Password hashing with BCrypt
- A custom `UserDetailsService` backed by a real database table
- A `OncePerRequestFilter` that validates tokens on every request
- Method-level authorization with `@PreAuthorize`
- Resource ownership enforcement (users can only touch their own tasks)

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

1. Create the database:
```sql
   CREATE DATABASE task_manager_db;
```
2. Update `src/main/resources/application.properties` if your Postgres credentials differ from `postgres`/`library123`.
3. Run the app:
```bash
   mvn spring-boot:run
```
   Tables (`users`, `tasks`) are auto-created on first run.

The API runs on `http://localhost:8081` (not 8080, to avoid clashing with other local Spring Boot projects).

## Authentication Flow

1. `POST /api/auth/register` → creates a user, returns a JWT
2. `POST /api/auth/login` → verifies credentials, returns a JWT
3. Every subsequent request to a protected endpoint must include:
4. Tokens expire after 1 hour (`app.jwt.expiration-ms` in `application.properties`) — after that, log in again for a new one.

## API Reference

### Auth (public — no token required)

| Method | Path | Body |
|---|---|---|
| POST | `/api/auth/register` | `{"username", "email", "password"}` |
| POST | `/api/auth/login` | `{"username", "password"}` |

Both return: `{"token", "username", "role"}`

### Tasks (protected — requires `Authorization: Bearer <token>`)

| Method | Path | Access | Body |
|---|---|---|---|
| POST | `/api/tasks` | any authenticated user | `{"title", "description"}` |
| GET | `/api/tasks` | own tasks only | — |
| GET | `/api/tasks/all` | **ADMIN only** | — |
| GET | `/api/tasks/{id}` | owner or admin | — |
| PUT | `/api/tasks/{id}` | owner or admin | `{"title", "description"}` |
| POST | `/api/tasks/{id}/complete` | owner or admin | — |
| DELETE | `/api/tasks/{id}` | owner or admin | — |

Non-owners attempting to access someone else's task get a `403 Forbidden`.

## Error Responses

Consistent JSON shape via a global exception handler:
```json
{
  "timestamp": "...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```
- `401` — bad credentials (login), or missing/invalid token (protected endpoints)
- `403` — authenticated, but not authorized (wrong owner, or missing ADMIN role)
- `404` — resource not found
- `409` — username or email already taken
- `400` — validation failure (includes a `fieldErrors` map)

## Project Structure

| Package | Contents |
|---|---|
| `entity/` | `User` (implements `UserDetails`), `Role` (enum), `Task` |
| `repository/` | Spring Data JPA repositories |
| `security/` | `JwtService`, `CustomUserDetailsService`, `JwtAuthenticationFilter`, `SecurityConfig` |
| `dto/` | Request/response records |
| `service/` | `AuthService` (register/login), `TaskService` (CRUD + ownership checks) |
| `controller/` | `AuthController`, `TaskController` |
| `exception/` | Custom exceptions + global handler |
## Testing It

```powershell
# Register
Invoke-RestMethod -Uri "http://localhost:8081/api/auth/register" -Method POST -ContentType "application/json" -Body '{"username": "kunal", "email": "kunal@test.com", "password": "password123"}'

# Login (copy the token from the response)
Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username": "kunal", "password": "password123"}'

# Create a task (replace <token>)
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks" -Method POST -ContentType "application/json" -Headers @{Authorization="Bearer <token>"} -Body '{"title": "Learn Spring Security", "description": "JWT + ownership checks"}'

# List your tasks
Invoke-RestMethod -Uri "http://localhost:8081/api/tasks" -Headers @{Authorization="Bearer <token>"}
```

## Notes

- To test the ADMIN-only endpoint, you'd need to manually update a user's `role` column to `ADMIN` in the database (there's no promote-to-admin endpoint by design — that's a deliberately privileged operation left out of this learning project).
- JWT secret in `application.properties` is a placeholder — never commit a real secret to source control in an actual project.