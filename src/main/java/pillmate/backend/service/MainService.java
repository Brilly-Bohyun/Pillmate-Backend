package pillmate.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pillmate.backend.dto.main.AdherenceRate;
import pillmate.backend.dto.main.BestRecord;
import pillmate.backend.dto.main.MainResponse;
import pillmate.backend.dto.main.MedicineAlarmRecord;
import pillmate.backend.dto.main.WorstRecord;
import pillmate.backend.entity.Alarm;
import pillmate.backend.entity.MedicinePerMember;
import pillmate.backend.repository.AlarmRepository;
import pillmate.backend.repository.MedicinePerMemberRepository;
import pillmate.backend.repository.MedicineRecordRepository;

import java.time.LocalTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MainService {
    private final AlarmService alarmService;
    private final AlarmRepository alarmRepository;
    private final MedicinePerMemberRepository medicinePerMemberRepository;
    private final MedicineRecordRepository medicineRecordRepository;

    public MainResponse show(final Long memberId, LocalTime currentTime) {
        return MainResponse.builder()
                .upcomingAlarm(alarmService.getUpcomingAlarm(memberId, currentTime))
                .medicineAlarmRecords(getMedicineRecords(memberId))
                .bestRecord(getBestRecord(memberId))
                .worstRecord(getWorstRecord(memberId))
                .build();
    }

    private List<MedicineAlarmRecord> getMedicineRecords(Long memberId) {
        List<Alarm> alarmList = alarmRepository.findAllByMemberId(memberId);

        // alarmList를 Alarm의 timeSlot 기준으로 정렬 (오전~오후 순)
        alarmList.sort(Comparator.comparing(alarm -> alarm.getTimeSlot().getPickerTime()));

        return alarmList.stream()
                .filter(alarm -> alarm.getIsAvailable().booleanValue() == Boolean.TRUE)
                .map(alarm -> {
                    // MedicineAlarmRecord 생성
                    return MedicineAlarmRecord.builder()
                            .MedicineId(alarm.getMedicinePerMember().getMedicine().getId())
                            .name(alarm.getMedicinePerMember().getMedicine().getName())
                            .time(alarm.getTimeSlot().getPickerTime())
                            .category(alarm.getMedicinePerMember().getMedicine().getCategory())
                            .isEaten(alarm.getIsEaten())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Integer getTotalAmount(MedicinePerMember medicinePerMember) {
        return medicinePerMember.getAmount() * medicinePerMember.getTimes() * (medicinePerMember.getDay());
    }

    private Integer getTakenAmount(Long memberId, Long medicineId) {
        return medicineRecordRepository.countByMemberIdAndMedicineIdAndIsEatenTrue(memberId, medicineId);
    }

    public List<AdherenceRate> getAllMedicineAdherenceRates(Long memberId) {
        List<MedicinePerMember> medicinePerMembers = medicinePerMemberRepository.findAllByMemberId(memberId);

        if (!medicinePerMembers.isEmpty()) {
            return medicinePerMembers.stream()
                    .map(mpm -> {
                        Integer totalAmount = getTotalAmount(mpm);
                        Integer takenAmount = getTakenAmount(memberId, mpm.getMedicine().getId());
                        return AdherenceRate.builder()
                                .medicineName(mpm.getMedicine().getName())
                                .taken(takenAmount)
                                .scheduled(totalAmount)
                                .rate((double) takenAmount / totalAmount)
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(AdherenceRate::getRate).reversed())
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    public BestRecord getBestRecord(Long memberId) {
        return BestRecord.from(getAllMedicineAdherenceRates(memberId).stream().findFirst().orElse(AdherenceRate.empty()));
    }

    public WorstRecord getWorstRecord(Long memberId) {
        List<AdherenceRate> rates = getAllMedicineAdherenceRates(memberId);
        return WorstRecord.from(rates.isEmpty() ? AdherenceRate.empty() : rates.get(rates.size() - 1));
    }
}
