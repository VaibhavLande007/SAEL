# SAEL Backend — Java Spring Boot 3

## Setup

### 1. Run the one-time SQL on your Azure DB
```
psql $CONNECTION_STRING -f docs/run_once_on_db.sql
```

### 2. Set environment variables
```
DB_HOST=yourserver.postgres.database.azure.com
DB_PORT=5432
DB_NAME=your_db
DB_USER=user@server
DB_PASS=yourpassword
JWT_SECRET=bXlTdXBlclNlY3JldEtleUZvclNBRUxQbGF0Zm9ybTIwMjVTZWN1cmVCYXNlNjRFbmNvZGVkU3RyaW5n
```

### 3. Run
```
mvn spring-boot:run
```

## API Base URL
`http://localhost:8080/api/v1`

## First Login
Use your existing user credentials with their tenant slug:
```json
POST /api/v1/auth/login
{
  "email": "admin@yourdomain.com",
  "password": "yourpassword",
  "tenant_slug": "your-slug"
}
```

## All Endpoints
| Method | Path | Role |
|--------|------|------|
| POST | /auth/login | Public |
| POST | /auth/refresh | Public |
| POST | /auth/logout | Any |
| GET  | /auth/me | Any |
| POST | /auth/change-password | Any |
| GET  | /networks | Any |
| POST | /networks | NETWORK_ADMIN+ |
| GET  | /networks/:id | Any |
| PATCH| /networks/:id | NETWORK_ADMIN+ |
| DELETE| /networks/:id | SYSTEM_ADMIN |
| GET  | /networks/:id/overview | Any |
| GET  | /networks/:id/trends | Any |
| GET  | /networks/:id/thresholds | Any |
| PATCH| /networks/:id/thresholds | NETWORK_ADMIN+ |
| GET  | /networks/:id/kpis/summary | Any |
| GET  | /networks/:id/hospitals | Any |
| POST | /networks/:id/hospitals | NETWORK_ADMIN+ |
| GET/PATCH/DELETE | /networks/:id/whatsapp/recipients | NETWORK_ADMIN+ |
| GET/PATCH | /networks/:id/whatsapp/settings | NETWORK_ADMIN+ |
| GET  | /networks/:id/whatsapp/logs | NETWORK_ADMIN+ |
| GET  | /hospitals/:id | Any |
| PATCH| /hospitals/:id | NETWORK_ADMIN+ |
| DELETE| /hospitals/:id | SYSTEM_ADMIN |
| GET  | /hospitals/:id/labs | Any |
| POST | /hospitals/:id/labs | NETWORK_ADMIN+ |
| GET  | /labs | Any |
| GET/PATCH/DELETE | /labs/:id | varies |
| GET  | /labs/:id/telemetry/live | Any |
| GET  | /labs/:id/telemetry/history | Any |
| GET  | /labs/:id/kpis | Any |
| POST/DELETE | /labs/:id/kpis | LAB_MANAGER+ |
| GET  | /alerts | Any |
| POST | /alerts/:id/acknowledge | Any |
| GET  | /insights | Any |
| POST | /insights/:id/read | Any |
| POST | /insights/generate | NETWORK_ADMIN+ |
| GET  | /users | NETWORK_ADMIN+ |
| POST | /users | NETWORK_ADMIN+ |
| PATCH/DELETE | /users/:id | NETWORK_ADMIN+ |
| GET/POST | /devices | varies |
| GET  | /admin/overview | SYSTEM_ADMIN |
| GET/POST/PATCH | /admin/tenants | SYSTEM_ADMIN |
| GET  | /admin/alerts | SYSTEM_ADMIN |
| GET  | /admin/devices | SYSTEM_ADMIN |
| GET  | /admin/kpis | SYSTEM_ADMIN |
| GET  | /admin/activity | SYSTEM_ADMIN |
