package com.example.api.clinic;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ClinicDomain {

    private ClinicDomain() {
    }

    public enum Specialty {
        CARDIOLOGY("cardiologia"),
        PEDIATRICS("pediatria"),
        DERMATOLOGY("dermatologia"),
        GENERAL_MEDICINE("medicina-general");

        private final String apiValue;

        Specialty(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }

        public static Specialty fromValue(String rawValue) {
            String normalizedValue = normalize(rawValue);
            for (Specialty specialty : values()) {
                if (normalize(specialty.apiValue).equals(normalizedValue)
                        || normalize(specialty.name()).equals(normalizedValue)) {
                    return specialty;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported specialty: " + rawValue);
        }
    }

    public enum AppointmentStatus {
        SCHEDULED,
        CANCELLED
    }

    public enum ExamType {
        COMPLETE_BLOOD_COUNT("hemograma"),
        GLUCOSE("glicemia"),
        LIPID_PROFILE("perfil-lipidico");

        private final String apiValue;

        ExamType(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }

        public static ExamType fromValue(String rawValue) {
            String normalizedValue = normalize(rawValue);
            for (ExamType examType : values()) {
                if (normalize(examType.apiValue).equals(normalizedValue)
                        || normalize(examType.name()).equals(normalizedValue)) {
                    return examType;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported exam: " + rawValue);
        }
    }

    public record CreatePatientRequest(
            String firstName,
            String lastName,
            String document,
            String email,
            String phone,
            String password,
            List<String> allergies) {

        public CreatePatientRequest {
            allergies = safeList(allergies);
        }
    }

    public record CreateAppointmentRequest(
            Long patientId,
            String specialty,
            LocalDateTime appointmentDate) {
    }

    public record MedicationRequest(
            String name,
            String dose,
            String duration) {
    }

    public record CreatePrescriptionRequest(
            Long patientId,
            List<MedicationRequest> medications) {

        public CreatePrescriptionRequest {
            medications = safeList(medications);
        }
    }

    public record CreateLaboratoryRequest(
            Long patientId,
            List<String> exams) {

        public CreateLaboratoryRequest {
            exams = safeList(exams);
        }
    }

    public record Patient(
            Long id,
            String firstName,
            String lastName,
            String document,
            String email,
            String phone,
            List<String> allergies) {

        public Patient {
            allergies = safeList(allergies);
        }
    }

    public record DoctorAvailability(
            Long id,
            String fullName,
            String specialty,
            List<LocalDateTime> availableSlots) {

        public DoctorAvailability {
            availableSlots = List.copyOf(availableSlots);
        }
    }

    public record Appointment(
            Long id,
            Long patientId,
            Long doctorId,
            String doctorName,
            String specialty,
            LocalDateTime appointmentDate,
            AppointmentStatus status,
            String reminder) {
    }

    public record ConsultationRecord(
            Long id,
            Long patientId,
            LocalDate consultationDate,
            String summary,
            String diagnosis) {
    }

    public record PrescribedMedication(
            String name,
            String dose,
            String duration) {
    }

    public record Prescription(
            Long id,
            Long patientId,
            LocalDateTime issuedAt,
            List<PrescribedMedication> medications,
            String warning) {

        public Prescription {
            medications = List.copyOf(medications);
        }
    }

    public record LabExamResult(
            String examName,
            String measuredValue,
            String referenceRange,
            String status) {
    }

    public record LaboratoryOrder(
            Long id,
            Long patientId,
            LocalDateTime createdAt,
            List<LabExamResult> results) {

        public LaboratoryOrder {
            results = List.copyOf(results);
        }
    }

    public record CompleteHistoryResponse(
            Patient patient,
            List<String> allergies,
            List<ConsultationRecord> consultations,
            List<Appointment> pastAppointments,
            List<Prescription> prescriptions,
            List<LaboratoryOrder> laboratoryOrders) {

        public CompleteHistoryResponse {
            allergies = safeList(allergies);
            consultations = List.copyOf(consultations);
            pastAppointments = List.copyOf(pastAppointments);
            prescriptions = List.copyOf(prescriptions);
            laboratoryOrders = List.copyOf(laboratoryOrders);
        }
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    public static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .trim();
    }

    public static boolean hasAnyText(String value) {
        return value != null && !value.isBlank();
    }

    public static boolean containsIgnoreCase(List<String> values, String candidate) {
        String normalizedCandidate = normalize(candidate);
        return values.stream()
                .filter(Objects::nonNull)
                .map(ClinicDomain::normalize)
                .anyMatch(normalizedCandidate::equals);
    }
}
