package com.example.api.clinic;

import com.example.api.clinic.ClinicDomain.CreatePatientRequest;
import com.example.api.clinic.ClinicDomain.DoctorAvailability;
import com.example.api.clinic.ClinicDomain.MedicationRequest;
import com.example.api.clinic.ClinicDomain.Patient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.fixtures.enabled", havingValue = "true", matchIfMissing = true)
public class ClinicFixtureLoader implements ApplicationRunner {

    private final ClinicFacade clinicFacade;
    private final AgendaService agendaService;
    private final MedicalRecordService medicalRecordService;

    public ClinicFixtureLoader(
            ClinicFacade clinicFacade,
            AgendaService agendaService,
            MedicalRecordService medicalRecordService) {
        this.clinicFacade = clinicFacade;
        this.agendaService = agendaService;
        this.medicalRecordService = medicalRecordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Patient maria = clinicFacade.registerPatient(new CreatePatientRequest(
                "Maria",
                "Gonzalez",
                "CC-900001",
                "maria.gonzalez@example.com",
                "3001111111",
                "maria123",
                List.of("penicillin")));

        Patient juan = clinicFacade.registerPatient(new CreatePatientRequest(
                "Juan",
                "Ramirez",
                "CC-900002",
                "juan.ramirez@example.com",
                "3002222222",
                "juan123",
                List.of("ibuprofen")));

        loadPatientHistory(maria, "cardiologia", "medicina-general", List.of("hemograma", "glicemia"));
        loadPatientHistory(juan, "dermatologia", "pediatria", List.of("perfil-lipidico"));
    }

    private void loadPatientHistory(
            Patient patient,
            String futureSpecialty,
            String historicalSpecialty,
            List<String> exams) {
        medicalRecordService.registerConsultation(
                patient.id(),
                "Initial clinical assessment",
                "Stable condition",
                LocalDate.now().minusDays(45));

        medicalRecordService.registerConsultation(
                patient.id(),
                "Follow-up evaluation",
                "Requires periodic monitoring",
                LocalDate.now().minusDays(20));

        agendaService.createHistoricalAppointment(
                patient.id(),
                historicalSpecialty,
                LocalDateTime.now().minusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0));

        clinicFacade.generarPrescripcion(
                patient.id(),
                List.of(new MedicationRequest("loratadine", "10 mg", "7 days")));

        clinicFacade.solicitarExamenes(patient.id(), exams);

        List<DoctorAvailability> doctorAvailabilities = agendaService.listAvailableDoctors(futureSpecialty);
        if (!doctorAvailabilities.isEmpty() && !doctorAvailabilities.get(0).availableSlots().isEmpty()) {
            clinicFacade.agendarCita(
                    patient.id(),
                    futureSpecialty,
                    doctorAvailabilities.get(0).availableSlots().get(0));
        }
    }
}
