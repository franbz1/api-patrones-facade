package com.example.api.clinic;

import com.example.api.clinic.ClinicDomain.ConsultationRecord;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class MedicalRecordService {

    private final AtomicLong consultationSequence = new AtomicLong(0);
    private final Map<Long, List<ConsultationRecord>> consultationsByPatient = new ConcurrentHashMap<>();

    public void initializeRecord(Long patientId) {
        consultationsByPatient.putIfAbsent(patientId, new java.util.concurrent.CopyOnWriteArrayList<>());
    }

    public ConsultationRecord registerConsultation(Long patientId, String summary, String diagnosis, LocalDate consultationDate) {
        initializeRecord(patientId);

        ConsultationRecord consultationRecord = new ConsultationRecord(
                consultationSequence.incrementAndGet(),
                patientId,
                consultationDate,
                ClinicDomain.requireText(summary, "summary"),
                ClinicDomain.requireText(diagnosis, "diagnosis"));

        consultationsByPatient.get(patientId).add(consultationRecord);
        return consultationRecord;
    }

    public List<ConsultationRecord> getConsultations(Long patientId) {
        initializeRecord(patientId);
        return consultationsByPatient.get(patientId).stream()
                .sorted(Comparator.comparing(ConsultationRecord::consultationDate).reversed())
                .toList();
    }
}
