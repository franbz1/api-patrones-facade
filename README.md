# Medical Clinic Backend

This module contains the Spring Boot backend for the medical clinic workshop. It exposes a simple REST API for the frontend, while internally coordinating multiple in-memory subsystems through a facade.

## Stack

- Java 17
- Spring Boot 3
- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-test`

## Current Scope

The backend is intentionally implemented without a database. All data lives in memory while the application is running.

That means:

- Restarting the application resets patients, appointments, prescriptions, and laboratory orders
- There is no persistence layer, repository layer, or external service integration
- The API is designed for the workshop demo and for frontend integration

## Run Locally

### Requirements

- Java 17 installed
- Maven 3.9+ installed and available in your `PATH`

### Start the server

From the `api` folder:

```bash
mvn spring-boot:run
```

By default the server starts on:

- `http://localhost:8080`

Useful endpoints to confirm the application is running:

- `GET http://localhost:8080/api/health`
- `POST http://localhost:8080/api/auth/login`

### Package and run as a jar

```bash
mvn clean package
java -jar target/api-0.0.1-SNAPSHOT.jar
```

### JWT configuration

The application reads these local properties from `src/main/resources/application.properties`:

- `app.security.jwt.secret`
- `app.security.jwt.expiration-minutes`
- `app.fixtures.enabled`

For a real environment, move the secret to an environment variable or secret manager.

### Demo fixtures on startup

When the application starts, it automatically loads demo fixtures in memory through `ClinicFixtureLoader`.

The loader uses the existing services and facade to create:

- demo patient accounts
- demo patients
- historical consultations
- historical appointments
- prescriptions
- laboratory orders
- one future appointment per seeded patient when availability exists

If you need to disable fixtures, set:

```properties
app.fixtures.enabled=false
```

## Package Structure

The current implementation lives under `com.example.api.clinic` and `com.example.api.auth`.

- `ClinicController`: exposes the REST endpoints under `/api/clinica`
- `ClinicFacade`: orchestrates cross-service flows and hides subsystem complexity
- `ClinicDomain`: shared request/response models, enums, and validation helpers
- `PatientService`: patient registration, unique document validation, patient profile access
- `AgendaService`: predefined doctors, specialties, availability, and appointment scheduling
- `MedicalRecordService`: stores consultation records associated with a patient
- `PrescriptionService`: creates prescriptions and validates medications against allergies
- `LaboratoryService`: simulates laboratory orders and exam results
- `AuthController`: exposes login and logout endpoints
- `AuthService`: validates in-memory users and revokes JWTs on logout
- `JwtService`: creates and validates JWT tokens using HMAC-SHA256
- `JwtAuthenticationFilter`: extracts the `Bearer` token and authenticates requests
- `SecurityConfig`: defines public and protected routes

## Architecture

The backend follows a small facade-oriented design:

1. The frontend talks only to REST endpoints.
2. `AuthController` provides authentication with JWT.
3. `ClinicController` delegates business flows to `ClinicFacade`.
4. `ClinicFacade` coordinates the required subsystems for each use case.
5. Subsystems keep their own in-memory state.

This keeps the frontend contract simple while still reflecting the workshop requirement that real clinic operations involve multiple internal services.

## Authentication

JWT authentication is now available.

### Public routes

- `GET /api/health`
- `POST /api/auth/login`
- `POST /api/clinica/paciente`

### Protected routes

- `POST /api/auth/logout`
- `GET /api/clinica/medicos`
- `POST /api/clinica/cita`
- `GET /api/clinica/historia/{patientId}`
- `POST /api/clinica/prescripcion`
- `POST /api/clinica/laboratorio`

### Patient-linked accounts

Authentication is linked to patient records.

That means:

- each patient account is created through `POST /api/clinica/paciente`
- login uses the patient document plus password
- each JWT includes the corresponding `patientId`
- protected patient flows can only operate on the authenticated patient data

### Fixture credentials

When startup fixtures are enabled, these patient accounts are created automatically:

- `CC-900001` / `maria123`
- `CC-900002` / `juan123`

### `POST /api/auth/login`

Returns a JWT that must be sent in the `Authorization` header.

Request body:

```json
{
  "document": "CC-900001",
  "password": "maria123"
}
```

Response body:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-04-10T20:10:00Z",
  "username": "CC-900001",
  "patientId": 1,
  "roles": ["ROLE_PATIENT"]
}
```

### `POST /api/auth/logout`

Invalidates the current JWT in memory until it expires.

Required header:

```text
Authorization: Bearer <token>
```

Response body:

```json
{
  "message": "Logout successful",
  "loggedOutAt": "2026-04-10T18:10:00Z"
}
```

### Frontend usage

The frontend should:

1. Call `POST /api/auth/login`
2. Store the returned `accessToken`
3. Send `Authorization: Bearer <token>` on protected requests
4. Call `POST /api/auth/logout` when the user signs out

Recommended demo credentials for local development:

- `CC-900001` / `maria123`

## Facade Responsibilities

`ClinicFacade` is the main orchestration layer and implements the required workshop flows:

- `registerPatient(...)`
  - Registers the patient in `PatientService`
  - Initializes a clinical record in `MedicalRecordService`

- `agendarCita(...)`
  - Validates that the patient exists using `PatientService`
  - Schedules the appointment using `AgendaService`

- `verHistoriaCompleta(...)`
  - Loads patient data from `PatientService`
  - Consolidates consultations from `MedicalRecordService`
  - Consolidates past appointments from `AgendaService`
  - Consolidates prescriptions from `PrescriptionService`
  - Consolidates laboratory orders from `LaboratoryService`

- `generarPrescripcion(...)`
  - Validates the patient and allergies with `PatientService`
  - Creates the prescription in `PrescriptionService`
  - Registers a consultation event in `MedicalRecordService`

- `solicitarExamenes(...)`
  - Validates the patient with `PatientService`
  - Creates the laboratory order in `LaboratoryService`
  - Registers a consultation event in `MedicalRecordService`

## In-Memory Business Rules

### Patient

- A patient document must be unique
- Allergies are stored as part of the patient profile

### Agenda

- At least 3 specialties are available
- 5 doctors are predefined in memory
- Each doctor starts with generated future slots
- Scheduling consumes one available slot
- Each successful appointment includes a reminder message

### Prescription

- At least one medication is required
- A prescription is rejected if any medication matches a patient allergy

### Laboratory

- Supported exams:
  - `hemograma`
  - `glicemia`
  - `perfil-lipidico`
- Results are simulated and include a reference range

## REST API

Base path: `/api/clinica`

### `POST /paciente`

Registers a patient.

Request body:

```json
{
  "firstName": "Ana",
  "lastName": "Lopez",
  "document": "CC-100",
  "email": "ana@example.com",
  "phone": "3001234567",
  "password": "ana123",
  "allergies": ["penicillin"]
}
```

Notes:

- Spanish aliases are also accepted for some fields, for example `nombres`, `apellidos`, `documento`, `correo`, `telefono`, `alergias`
- Patient registration now creates the patient login account as well

### `GET /medicos?especialidad=cardiologia`

Returns doctors and currently available slots.

Authentication:

- Requires `Authorization: Bearer <token>`

Supported specialty values:

- `cardiologia`
- `pediatria`
- `dermatologia`
- `medicina-general`

If `especialidad` is omitted, all doctors are returned.

### `POST /cita`

Schedules an appointment.

Authentication:

- Requires `Authorization: Bearer <token>`

Request body:

```json
{
  "patientId": 1,
  "specialty": "cardiologia",
  "appointmentDate": "2026-04-12T09:00:00"
}
```

Important:

- `appointmentDate` must match one of the slots returned by `/medicos`
- Dates in the API use ISO-8601 format

### `GET /historia/{patientId}`

Returns the complete clinical history used by the frontend.

Authentication:

- Requires `Authorization: Bearer <token>`

Response sections:

- `patient`
- `allergies`
- `consultations`
- `pastAppointments`
- `prescriptions`
- `laboratoryOrders`

### `POST /prescripcion`

Creates a prescription after validating allergies.

Authentication:

- Requires `Authorization: Bearer <token>`

Request body:

```json
{
  "patientId": 1,
  "medications": [
    {
      "name": "loratadine",
      "dose": "10 mg",
      "duration": "7 days"
    }
  ]
}
```

### `POST /laboratorio`

Creates a laboratory order with simulated results.

Authentication:

- Requires `Authorization: Bearer <token>`

Request body:

```json
{
  "patientId": 1,
  "exams": ["hemograma", "glicemia"]
}
```

## Frontend Integration Guidance

For the frontend agent, the recommended flow is:

1. Authenticate with `POST /api/auth/login`
2. Create a patient with `POST /api/clinica/paciente`
3. Fetch available doctors with `GET /api/clinica/medicos?especialidad=...`
4. Pick one of the returned slots and schedule it with `POST /api/clinica/cita`
5. Use `GET /api/clinica/historia/{patientId}` as the main consolidated patient dashboard source
6. Use `POST /api/clinica/prescripcion` and `POST /api/clinica/laboratorio` for secondary flows

The most useful endpoint for a dashboard is `GET /api/clinica/historia/{patientId}` because it already aggregates data from multiple subsystems.

## Error Behavior

The API currently uses Spring default error responses through `ResponseStatusException`.

Typical failure cases:

- `400 Bad Request`
  - Missing required fields
  - Unsupported specialty
  - Unsupported exam
  - Past appointment date
  - Medication blocked by allergies

- `401 Unauthorized`
  - Missing token on protected routes
  - Invalid credentials
  - Expired token
  - Revoked token after logout

- `403 Forbidden`
  - The authenticated patient is trying to access another patient record

- `404 Not Found`
  - Patient not found
  - Appointment not found

- `409 Conflict`
  - Duplicate patient document

## Tests

Integration tests live in `src/test/java/com/example/api/ApiApplicationTests.java`.

Current coverage validates:

- patient registration
- duplicate document rejection
- login and logout with JWT
- protected route enforcement
- appointment scheduling
- allergy validation for prescriptions
- consolidated history response

## Known Limitations

- No persistence
- No pagination
- No update endpoints
- Appointment cancellation exists only at service level for now, not as a REST endpoint
- Past appointments only appear in history once their datetime is before the current system time
- Tokens are patient-scoped; there is no separate admin or staff authorization model yet
