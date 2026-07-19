# Take-Home Exercise Write-Up
**URL Shortener & Link Analytics**

---

### 1. What did you ask the AI to do, and what did you write or decide yourself?
- **AI Requests**: I directed the AI assistant to scaffold the Spring Boot directory structure, generate basic boilerplate code for JPA entities, repositories, and exception handling, and implement a standard Base62 encoding utility.
- **My Decisions & Code**:
  - **Deterministic Duplicates**: I intentionally chose to map duplicate URLs to the same short code. This optimizes storage efficiency, permits easy caching, and is deterministic.
  - **Safe Alias Constraints**: I strengthened custom alias validation. By using a specific `@Pattern(regexp = "^$|^[A-Za-z0-9_-]{3,30}$")` check, I ensured aliases are URL-safe, avoiding characters like `/` or spaces, and length-restricted to `3-30` characters, while safely allowing empty strings.
  - **Robust URL Verification**: I rejected pure regex-based validation in favor of `java.net.URI` scheme and host validation, which prevents invalid protocol schemes (e.g. `ftp` if unsupported or garbage schemas).

---

### 2. Where did you override, correct, or throw away the AI’s output — and why?
- **Jackson & Spring Boot 4 imports**: The AI generated boilerplate imports assuming Spring Boot 3.x and Jackson 2.x namespaces (e.g. `com.fasterxml.jackson` and `org.springframework.boot.test.autoconfigure.web.servlet`). Because this project uses **Spring Boot 4.1.0** and **Jackson 3**, I corrected these namespaces to use `tools.jackson.databind` and `org.springframework.boot.webmvc.test.autoconfigure`.
- **First-Level Cache in Concurrency**: Under concurrent requests, the initial lock-free design suffered from race conditions where threads read dirty `TEMP_` codes before the final Base62 conversion completed. I corrected the implementation by adding a **spin-lock loop** combined with `entityManager.clear()`. Without clearing the persistence context, Hibernate's L1 cache would return dirty/cached entity states without refetching the updated `shortCode` value from H2.
- **Transactional Rollback Behavior**: I discarded method-level `@Transactional` on the service's `shorten` method. In Spring JPA, catching unique constraint violations within a transaction marks the transaction as rollback-only. This prevents subsequent queries (like finding the existing code from the winning thread) from executing. Going non-transactional at the service level allowed separate transactions for each repository action, facilitating a successful catch-and-retry query.

---

### 3. The two or three biggest trade-offs you made, and the alternatives you considered.
- **Dual-Save vs. UUID Keys**:
  *Trade-off*: To get an auto-generated numeric database ID for the Base62 encoder while enforcing `@Column(nullable=false, unique=true)` on the `shortCode` column, we must save the record twice (first with a temporary UUID string, then with the Base62 string).
  *Alternative*: Making the column nullable would eliminate the first save. However, a nullable `shortCode` represents poor database hygiene for a URL shortener, risking orphan null records. We chose data integrity over a single-write optimization.
- **Spin-Lock Waiting vs. Database Pessimistic Locks**:
  *Trade-off*: When two threads attempt to shorten the same URL simultaneously, they might both check and miss, trying to insert. A pessimistic write lock (`SELECT ... FOR UPDATE`) fails to lock a non-existent row in H2 MVCC.
  *Alternative*: We chose to let the database enforce the unique index constraint on `originalUrl`. The losing thread catches the constraint violation, clears its Hibernate context, and spins-waits (up to 20 retries, 50ms interval) for the winning thread's final Base62 code to be committed. This provides robust cross-instance compatibility without complex distributed locks (e.g., Redis).

---

### 4. What’s missing, or what you’d do with another day?
- **Global Counter / Snowflake IDs**: The dual-save step (inserting a temporary code to fetch an ID) is a performance bottleneck under high write loads. With another day, I would replace database-generated auto-increment IDs with a high-performance **Snowflake ID Generator** or a centralized Redis/Zookeeper sequence generator, allowing us to compute the Base62 code *before* inserting into the database.
- **Distributed Caching (Redis)**: Frequently requested short codes should bypass the relational database entirely. Implementing a cache-aside pattern using Redis would drastically improve retrieval times (`GET /{code}`) and scale the system's throughput.
- **Analytics Tracking**: The prompt mentions "Link Analytics". Adding an asynchronous listener (via Spring `@EventListener` or Kafka) to record click counts, geo-IP locations, user-agents, and timestamps without blocking the redirect flow.
