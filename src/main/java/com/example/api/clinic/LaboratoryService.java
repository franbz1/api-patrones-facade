package com.example.api.clinic;

import com.example.api.clinic.ClinicDomain.ExamType;
import com.example.api.clinic.ClinicDomain.LabExamResult;
import com.example.api.clinic.ClinicDomain.LaboratoryOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LaboratoryService {

    private final AtomicLong laboratorySequence = new AtomicLong(0);
    private final Map<Long, List<LaboratoryOrder>> ordersByPatient = new ConcurrentHashMap<>();

    public LaboratoryOrder requestExams(Long patientId, List<String> exams) {
        if (exams == null || exams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one exam is required");
        }

        List<LabExamResult> results = exams.stream()
                .map(ExamType::fromValue)
                .map(this::simulateResult)
                .toList();

        LaboratoryOrder order = new LaboratoryOrder(
                laboratorySequence.incrementAndGet(),
                patientId,
                LocalDateTime.now(),
                results);

        ordersByPatient
                .computeIfAbsent(patientId, ignored -> new CopyOnWriteArrayList<>())
                .add(order);

        return order;
    }

    public List<LaboratoryOrder> getOrders(Long patientId) {
        return ordersByPatient.getOrDefault(patientId, List.of()).stream().toList();
    }

    private LabExamResult simulateResult(ExamType examType) {
        return switch (examType) {
            case COMPLETE_BLOOD_COUNT -> new LabExamResult(
                    examType.apiValue(),
                    "Hemoglobin: 14.2 g/dL",
                    "13.0 - 17.0 g/dL",
                    "normal");
            case GLUCOSE -> new LabExamResult(
                    examType.apiValue(),
                    "92 mg/dL",
                    "70 - 99 mg/dL",
                    "normal");
            case LIPID_PROFILE -> new LabExamResult(
                    examType.apiValue(),
                    "LDL: 118 mg/dL",
                    "0 - 129 mg/dL",
                    "borderline");
        };
    }
}
