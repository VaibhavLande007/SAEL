# SAEL Intelligence Platform — Backend Engine (Java Spring Boot)

This repository contains the backend engine for the SAEL Intelligence Platform, built using Java 21 and Spring Boot 3. It serves REST and Server-Sent Events (SSE) endpoints to the Tenant and Super Admin frontend dashboards.

---

## 1. Project Overview

### Purpose
The SAEL Backend handles multi-tenant environmental monitoring data, user management, clinic hierarchies, alert configurations, alerts, and outcomes correlation for IVF laboratories.

### Architecture
```
┌──────────────────────────────────────┐
│          Browser JavaScript          │
└──────────┬─────────────────┬─────────┘
           │                 │
     (REST Path)         (SSE Stream)
           │                 │
           ▼                 ▼
┌──────────────────┐ ┌──────────────────┐
│  REST Controller │ │   SSE Controller │
└──────────┬───────┘ └───────┬──────────┘
           │                 │
           ▼                 ▼
┌──────────────────────────────────────┐
│            Service Layer             │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│           Repository Layer           │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│       PostgreSQL Database Schema     │
└──────────────────────────────────────┘
```

The system separates concerns into three distinct layers:
* **Controller Layer**: Exposes endpoints and handles request model validation. Supports dual authorization filters (JWT bearer token validation + administrative secret validation).
* **Service Layer**: Implements business transactions, threshold validation, and default alert rule seeding.
* **Repository Layer (Spring Data JPA)**: Queries and aggregates time-series telemetry rollups, alert history, and tenant statistics.

### Key Modules
1. **Auth & Security**: Stateless JWT-based authentication with standard BCrypt password hashing.
2. **Hierarchy CRUD**: Structure setup for Networks, Hospital clinics, and Labs.
3. **Alert Engine**: Validates environment values and dispatches warning/critical alarms.
4. **WhatsApp Integration**: Manages recipient contacts, notification categories, and message dispatch logging.
5. **Real-time SSE**: Streams live sensor updates directly to clients via `SseEmitter`.
6. **Telemetry & Trends**: Long-term clinical outcome logging and hourly rollup summaries.

---

## 2. Prerequisites
* **Java SDK**: Version `21`
* **Apache Maven**: Version `3.9.x`
* **PostgreSQL**: Version `15` or newer (Azure Database for PostgreSQL Flexible Server)

---

## 3. Environment Setup

### Environment Variables
Configure these variables in your system or create a `.env` file:

| Parameter | Configuration Property | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `DB_HOST` | `spring.datasource.url` | `sael.postgres.database.azure.com` | PostgreSQL Host Address |
| `DB_PORT` | `spring.datasource.url` | `5432` | PostgreSQL Port |
| `DB_NAME` | `spring.datasource.url` | `sael_db` | PostgreSQL Database Schema |
| `DB_USER` | `spring.datasource.username` | `sael` | Database Username |
| `DB_PASS` | `spring.datasource.password` | `Divine@59_Rra` | Database Password |
| `ADMIN_SECRET` | `admin.secret` | `9f7Kx2PqLm8Rw4NzYt6Vc3Hd1Bj5As0Q` | Secret token for Super Admin dashboard calls |
| `JWT_SECRET` | `jwt.secret` | `bXlTdXBlclNlY3...` (Base64 key) | JWT signing secret key |

---

## 4. Database Setup

### 1. Database Creation
Connect to your PostgreSQL server and create the database:
```sql
CREATE DATABASE sael_db;
```

### 2. Apply Schema & Migrations
Prisma ORM owns the database schema migrations. In the frontend root folder, run:
```bash
pnpm --filter=@sael/db db:migrate-once
```

### 3. Seed Reference Tables & Roles
Run the SQL bootstrap script to seed default system roles:
```bash
psql -h <DB_HOST> -U <DB_USER> -d sael_db -f docs/run_once_on_db.sql
```

---

## 5. Running the Application

### IntelliJ IDEA Setup
1. Open IntelliJ and click **File -> Open**. Select this folder (`sael-v2`).
2. Go to **Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors**. Check the box for **Enable annotation processing**.
3. Set your project SDK to Java 21 in **Project Structure**.
4. Right-click `SaelApplication.java` and click **Run**.

### Maven CLI Commands
Build and package the application:
```bash
# Compile classes
mvn clean compile

# Build executable JAR
mvn clean package -DskipTests
```

Run the Spring Boot application:
```bash
# Overriding Java Home path if required
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"

# Launch Spring Boot
mvn spring-boot:run
```

---

## 6. Swagger / OpenAPI Documentation

Springdoc-openapi handles automatic API documentation. When the application is running, access the documentation via:

* **Swagger UI URL**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI JSON URL**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
* **OpenAPI YAML URL**: [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

### JWT Authentication in Swagger UI
1. Open Swagger UI in the browser.
2. Click the **Authorize** button in the top right.
3. For `bearerAuth`, enter your JWT token (e.g. `Bearer <your-token>`).
4. For `x-admin-secret`, enter the administrative secret (`9f7Kx2PqLm8Rw4NzYt6Vc3Hd1Bj5As0Q`).

---

## 7. Authentication Flow

### Login API
Request a JWT token by providing your email and password:
* **Endpoint**: `POST http://localhost:8080/api/v1/auth/login`
* **Content-Type**: `application/json`
* **Request Body**:
  ```json
  {
    "email": "nilesh@gmail.com",
    "password": "test123"
  }
  ```

### Using the Token
Copy the returned `accessToken` and pass it in the headers of subsequent requests:
```http
Authorization: Bearer <your-jwt-token>
```

---

## 8. API Modules Summary

### Auth Module
* `POST /api/v1/auth/login` - Authenticates user and returns access token.
* `POST /api/v1/auth/logout` - Terminates the active session.
* `GET /api/v1/auth/me` - Returns active user details.
* `POST /api/v1/auth/change-password` - Updates user password.

### Configuration Module
* `GET /api/v1/config` - Fetches network name, alert thresholds, and notification toggles.
* `PUT /api/v1/config/thresholds` - Saves updated parameter thresholds.
* `POST /api/v1/config/toggles` - Updates system alert preferences.
* `GET /api/v1/config/centres` - Retrieves hospitals, labs, and device mapping.

### Active Alerts Module
* `GET /api/v1/alerts` - Returns historical logs of warn/crit alerts.
* `POST /api/v1/alerts/{alertId}/acknowledge` - Acknowledges an active alert.

### Telemetry Module
* `GET /api/v1/labs/{labId}/telemetry/live` - Fetches current metrics for real-time widgets.
* `GET /api/v1/labs/{labId}/telemetry/history` - Fetches historical data points.
* `GET /api/v1/telemetry/stream` - SSE stream for live telemetry gauges.

### Trends & KPIs Module
* `GET /api/v1/trends` - Long-term hourly trend analysis.
* `GET /api/v1/kpis` - Fetches outcomes entries.
* `POST /api/v1/kpis` - Submits a weekly clinical outcome KPI entry.
* `DELETE /api/v1/kpis/{kpiId}` - Deletes an outcome entry.

---

## 9. Troubleshooting

### 1. 401 Unauthorized
* **Cause**: Missing or expired `Authorization` header.
* **Fix**: Log in again to get a fresh token. If testing Super Admin endpoints, ensure the `x-admin-secret` header is present and matches the configured backend secret.

### 2. 403 Forbidden
* **Cause**: Role mismatch or invalid token signature.
* **Fix**: Ensure your user account is assigned the appropriate role (e.g. `SYSTEM_ADMIN` or `TENANT_ADMIN`). If using legacy tokens, check that the signature validation fallback is active.

### 3. Database Connection Issues
* **Cause**: Database host is unreachable or DB credentials are wrong.
* **Fix**: Verify your network connectivity to your Azure database server. Check that your client IP is whitelisted on the PostgreSQL firewall.

### 4. Swagger UI Not Loading
* **Cause**: Port collision or security filters blocking the resource path.
* **Fix**: Check that port `8080` is not locked by another process. Verify that `SecurityConfig.java` permits anonymous requests to `/swagger-ui/**` and `/v3/api-docs/**`.

---

## 10. Deployment

### Build Executable JAR
```bash
mvn clean package -DskipTests
```
This outputs a standalone `.jar` file in the `target/` directory:
```
sael-backend-1.0.0.jar
```

### Run on Server
Set the environment variables and run the jar using:
```bash
java -jar target/sael-backend-1.0.0.jar
```
This launches the integrated Tomcat container on port `8080`.
