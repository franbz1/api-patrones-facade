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

@SpringBootTest
@AutoConfigureMockMvc
class ApiApplicationTests {

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
                  "allergies": ["penicillin"]
                }
                """;

        mockMvc.perform(post("/api/clinica/paciente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.document").value("CC-100"));

        mockMvc.perform(post("/api/clinica/paciente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldScheduleAppointmentAndReturnReminder() throws Exception {
        Long patientId = createPatient("CC-101", "ibuprofen");
        String appointmentDate = getFirstAvailableSlot("cardiologia");

        String payload = """
                {
                  "patientId": %d,
                  "specialty": "cardiologia",
                  "appointmentDate": "%s"
                }
                """.formatted(patientId, appointmentDate);

        mockMvc.perform(post("/api/clinica/cita")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.specialty").value("cardiologia"))
                .andExpect(jsonPath("$.reminder").isNotEmpty());
    }

    @Test
    void shouldBlockPrescriptionWhenMedicationMatchesAllergy() throws Exception {
        Long patientId = createPatient("CC-102", "aspirin");

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
                """.formatted(patientId);

        mockMvc.perform(post("/api/clinica/prescripcion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnCompleteHistoryWithPrescriptionsAndLaboratoryOrders() throws Exception {
        Long patientId = createPatient("CC-103", "none");

        mockMvc.perform(post("/api/clinica/prescripcion")
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
                                """.formatted(patientId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clinica/laboratorio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": %d,
                                  "exams": ["hemograma", "glicemia"]
                                }
                                """.formatted(patientId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clinica/historia/{patientId}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.id").value(patientId))
                .andExpect(jsonPath("$.consultations.length()").value(2))
                .andExpect(jsonPath("$.prescriptions.length()").value(1))
                .andExpect(jsonPath("$.laboratoryOrders.length()").value(1));
    }

    private Long createPatient(String document, String allergy) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clinica/paciente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Carlos",
                                  "lastName": "Perez",
                                  "document": "%s",
                                  "email": "%s@example.com",
                                  "phone": "3001234567",
                                  "allergies": ["%s"]
                                }
                                """.formatted(document, document.toLowerCase(), allergy)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private String getFirstAvailableSlot(String specialty) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/clinica/medicos")
                        .param("especialidad", specialty))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doctors = objectMapper.readTree(result.getResponse().getContentAsString());
        return doctors.get(0).get("availableSlots").get(0).asText();
    }
}
