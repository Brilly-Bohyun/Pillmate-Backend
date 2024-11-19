package pillmate.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pillmate.backend.dto.main.AdherenceRate;
import pillmate.backend.dto.main.BestRecord;
import pillmate.backend.dto.main.MainResponse;
import pillmate.backend.dto.main.MedicineAlarmRecord;
import pillmate.backend.dto.main.MissedAlarm;
import pillmate.backend.dto.main.RemainingMedicine;
import pillmate.backend.dto.main.WorstRecord;
import pillmate.backend.entity.Alarm;
import pillmate.backend.entity.MedicinePerMember;
import pillmate.backend.repository.AlarmRepository;
import pillmate.backend.repository.MedicinePerMemberRepository;
import pillmate.backend.repository.MedicineRecordRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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
                .missedAlarms(getMissedAlarms(memberId))
                .medicineAlarmRecords(getMedicineRecords(memberId))
                .remainingMedicine(getRemainingMedicine(memberId))
                .bestRecord(getBestRecord(memberId))
                .worstRecord(getWorstRecord(memberId))
                .build();
    }

    private List<MissedAlarm> getMissedAlarms(Long memberId) {
        LocalTime now = LocalTime.now();
        return alarmRepository.findMissedAlarms(memberId, now).stream()
                .filter(alarm -> alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isAfter(LocalDate.now())
                        || alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isEqual(LocalDate.now()))
                .map(
                alarm -> MissedAlarm.builder()
                        .name(alarm.getMedicinePerMember().getMedicine().getName())
                        .time(alarm.getTimeSlot().getPickerTime())
                        .build()
        ).toList();
    }

    private List<MedicineAlarmRecord> getMedicineRecords(Long memberId) {
        List<Alarm> alarmList = alarmRepository.findAllByMemberId(memberId);

        // alarmList를 Alarm의 timeSlot 기준으로 정렬 (오전~오후 순)
        alarmList.sort(Comparator.comparing(alarm -> alarm.getTimeSlot().getPickerTime()));

        return alarmList.stream()
                .filter(alarm -> alarm.getIsAvailable().booleanValue() == Boolean.TRUE)
                .filter(alarm -> alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isAfter(LocalDate.now())
                        || alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isEqual(LocalDate.now()))
                .map(alarm -> {
                    // MedicineAlarmRecord 생성
                    return MedicineAlarmRecord.builder()
                            .alarmId(alarm.getId())
                            .medicineId(alarm.getMedicinePerMember().getMedicine().getId())
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

    private List<AdherenceRate> getAllMedicineAdherenceRates(Long memberId) {
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

    private List<RemainingMedicine> getRemainingMedicine(Long memberId) {
        List<MedicinePerMember> medicines = findMedicineByMemberId(memberId);
        return medicines.stream().map(medicinePerMember -> {
            LocalDate endDate = medicinePerMember.getCreated().plusDays(medicinePerMember.getDay());
            LocalDate today = LocalDate.now();
            Long daysLeft = getLeftDays(today, endDate);

            return RemainingMedicine.builder()
                    .name(medicinePerMember.getMedicine().getName())
                    .category(medicinePerMember.getMedicine().getCategory())
                    .day(daysLeft)
                    .build();
        }).toList();
    }

    private static Long getLeftDays(LocalDate today, LocalDate endDate) {
        Long daysLeft = ChronoUnit.DAYS.between(today, endDate);

        if (daysLeft < 0) {
            daysLeft = 0L;
        }

        return daysLeft;
    }

    private BestRecord getBestRecord(Long memberId) {
        return BestRecord.from(getBestRate(memberId));
    }

    private WorstRecord getWorstRecord(Long memberId) {
        AdherenceRate bestRate = getBestRate(memberId);
        List<AdherenceRate> rates = getAllMedicineAdherenceRates(memberId);

        AdherenceRate worstRate = rates.isEmpty() ? AdherenceRate.empty() : rates.get(rates.size() - 1);

        // 만약 worstRate와 bestRate가 같다면
        if (worstRate.equals(bestRate)) {
            // 리스트에 2개 이상의 요소가 있을 경우 그 이전 값을 선택
            if (rates.size() > 1) {
                worstRate = rates.get(rates.size() - 2);
            } else {
                // 이전 값이 없으면 빈 값을 반환
                worstRate = AdherenceRate.empty();
            }
        }

        return WorstRecord.from(worstRate);
    }

    private AdherenceRate getBestRate(Long memberId) {
        return getAllMedicineAdherenceRates(memberId).stream()
                .filter(adherenceRate -> adherenceRate.getTaken() != 0)
                .findFirst()
                .orElse(AdherenceRate.empty());
    }

    private List<MedicinePerMember> findMedicineByMemberId(Long memberId) {
        return medicinePerMemberRepository.findAllByMemberId(memberId);
    }
}
