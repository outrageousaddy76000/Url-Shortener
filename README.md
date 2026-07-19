# URL Shortener & Link Analytics

A clean, production-ready URL Shortener service built with Spring Boot 4, Spring Data JPA, and an in-memory H2 Database. The project is designed with concurrency safety, robust validation, and clean RESTful design patterns.

---

## Technical Stack
- **Framework**: Spring Boot 4.1.0
- **Java Version**: 21
- **Persistence**: Spring Data JPA
- **Database**: H2 Database (In-Memory)
- **Utilities**: Lombok, Jakarta Validation
- **Testing**: JUnit 5, MockMvc

---

## Core Features
1. **Shorten URL (`POST /shorten`)**:
   - Accepts a long URL and returns a unique, collision-free short code.
   - Validates URL structure (requires schema and host).
   - Supports optional custom alphanumeric aliases (length 3-30, allowing `_` and `-`).
   - Handles identical URL shortening deterministically (returns the same short code without creating database duplicates).
2. **Redirect (`GET /{code}`)**:
   - Performs a clean `301 Moved Permanently` HTTP redirect to the original URL.
   - Handles missing short codes gracefully with a `404 Not Found` response.
3. **High Concurrency Safety**:
   - Safe under heavy multi-threaded usage for both Base62 auto-generation and custom alias registrations.
   - Implements a spin-lock retry pattern combined with unique database constraints to prevent duplicate inserts and dirty reads without relying on heavy transaction rollback overhead.

---

## Installation & Running the Application

### Prerequisites
- JDK 21 installed.
- Maven (optional, wrapper is provided).

### Running the Server
To run the Spring Boot application, execute:
```bash
./mvnw spring-boot:run
```
The server will start on [http://localhost:8080](http://localhost:8080).

### Accessing H2 Database Console
The in-memory H2 console is enabled and can be accessed at:
- **URL**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **JDBC URL**: `jdbc:h2:mem:urlshortener`
- **Username**: `sa`
- **Password**: *(leave empty)*

---

## Running the Automated Tests
The project contains 14 integration and edge-case tests verifying controller mapping, redirect headers, duplicate handling, custom validation constraints, and high-concurrency race conditions.

To run all tests:
```bash
./mvnw test
```

---

## API Documentation & Curl Examples

### 1. Shorten URL (Auto-Generated Base62 Code)
```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://google.com"}'
```
**Response (200 OK)**:
```json
{
  "code": "1"
}
```

### 2. Shorten Same URL Again (Deterministic Code)
```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://google.com"}'
```
**Response (200 OK)**:
```json
{
  "code": "1"
}
```

### 3. Shorten URL with Custom Alias
```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://github.com", "alias": "github"}'
```
**Response (200 OK)**:
```json
{
  "code": "github"
}
```

### 4. Create Alias That Already Exists
```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://spring.io", "alias": "github"}'
```
**Response (409 Conflict)**:
```
Alias already exists
```

### 5. Redirect using Short Code
```bash
curl -i http://localhost:8080/github
```
**Response (301 Moved Permanently)**:
```http
HTTP/1.1 301 
Location: https://github.com
Content-Length: 0
```

### 6. Unknown Short Code
```bash
curl -i http://localhost:8080/random123
```
**Response (404 Not Found)**:
```
Code not found
```
