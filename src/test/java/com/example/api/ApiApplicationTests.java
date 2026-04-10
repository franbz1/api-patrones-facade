package com.example.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.fixtures.enabled=false")
@AutoConfigureMockMvc
class ApiApplicationTests {

    private static final String AUTHORIZATION = "Authorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterPatientAndRejectDuplicateDocument() throws Exception {
        String payload = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "document": "CC-100",
                  "email": "ana@example.com",
                  "phone": "3001234567",
                  "password": "ana123",
                  "allergies": ["penicillin"]
                }
                """;

        mockMvc.perform(post("/api/clinica/paciente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.document").value("CC-100"));

        mockMvc.perform(post("/api/clinica/paciente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldScheduleAppointmentAndReturnReminder() throws Exception {
        PatientSession patientSession = createPatient("CC-101", "ibuprofen", "patient101");
        String token = loginAndGetToken(patientSession.document(), patientSession.password());
        String appointmentDate = getFirstAvailableSlot(token, "cardiologia");

        String payload = """
                {
                  "patientId": %d,
                  "specialty": "cardiologia",
                  "appointmentDate": "%s"
                }
                """.formatted(patientSession.patientId(), appointmentDate);

        mockMvc.perform(post("/api/clinica/cita")
                        .header(AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientSession.patientId()))
                .andExpect(jsonPath("$.specialty").value("cardiologia"))
                .andExpect(jsonPath("$.reminder").isNotEmpty());
    }

    @Test
    void shouldBlockPrescriptionWhenMedicationMatchesAllergy() throws Exception {
        PatientSession patientSession = createPatient("CC-102", "aspirin", "patient102");
        String token = loginAndGetToken(patientSession.document(), patientSession.password());

        String payload = """
                {
                  "patientId": %d,
                  "medications": [
                    {
                      "name": "aspirin",
                      "dose": "500 mg",
                      "duration": "5 days"
                    }
                  ]
                }
                """.formatted(patientSession.patientId());

        mockMvc.perform(post("/api/clinica/prescripcion")
                        .header(AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnCompleteHistoryWithPrescriptionsAndLaboratoryOrders() throws Exception {
        PatientSession patientSession = createPatient("CC-103", "none", "patient103");
        String token = loginAndGetToken(patientSession.document(), patientSession.password());

        mockMvc.perform(post("/api/clinica/prescripcion")
                        .header(AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": %d,
                                  "medications": [
                                    {
                                      "name": "loratadine",
                                      "dose": "10 mg",
                                      "duration": "7 days"
                                    }
                                  ]
                                }
                                """.formatted(patientSession.patientId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clinica/laboratorio")
                        .header(AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": %d,
                                  "exams": ["hemograma", "glicemia"]
                                }
                                """.formatted(patientSession.patientId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clinica/historia/{patientId}", patientSession.patientId())
                        .header(AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.id").value(patientSession.patientId()))
                .andExpect(jsonPath("$.consultations.length()").value(2))
                .andExpect(jsonPath("$.prescriptions.length()").value(1))
                .andExpect(jsonPath("$.laboratoryOrders.length()").value(1));
    }

    @Test
    void shouldRequireJwtForProtectedClinicEndpoints() throws Exception {
        mockMvc.perform(get("/api/clinica/medicos")
                        .param("especialidad", "cardiologia"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoginAndLogoutWithJwt() throws Exception {
        PatientSession patientSession = createPatient("CC-104", "none", "patient104");
        String token = loginAndGetToken(patientSession.document(), patientSession.password());

        mockMvc.perform(post("/api/auth/logout")
                        .header(AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        mockMvc.perform(get("/api/clinica/medicos")
                        .header(AUTHORIZATION, bearerToken(token))
                        .param("especialidad", "cardiologia"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidAccessToAnotherPatientData() throws Exception {
        PatientSession patientOne = createPatient("CC-105", "none", "patient105");
        PatientSession patientTwo = createPatient("CC-106", "none", "patient106");
        String token = loginAndGetToken(patientOne.document(), patientOne.password());

        mockMvc.perform(get("/api/clinica/historia/{patientId}", patientTwo.patientId())
                        .header(AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isForbidden());
    }

    private PatientSession createPatient(String document, String allergy, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clinica/paciente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Carlos",
                                  "lastName": "Perez",
                                  "document": "%s",
                                  "email": "%s@example.com",
                                  "phone": "3001234567",
                                  "password": "%s",
                                  "allergies": ["%s"]
                                }
                                """.formatted(document, document.toLowerCase(), password, allergy)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new PatientSession(node.get("id").asLong(), document, password);
    }

    private String getFirstAvailableSlot(String token, String specialty) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/clinica/medicos")
                        .header(AUTHORIZATION, bearerToken(token))
                        .param("especialidad", specialty))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doctors = objectMapper.readTree(result.getResponse().getContentAsString());
        return doctors.get(0).get("availableSlots").get(0).asText();
    }

    private String loginAndGetToken(String document, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "document": "%s",
                                  "password": "%s"
                                }
                                """.formatted(document, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").isNumber())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    private record PatientSession(Long patientId, String document, String password) {
    }
}
