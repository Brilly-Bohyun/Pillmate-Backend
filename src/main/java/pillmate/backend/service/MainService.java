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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MainService {
    private final AlarmRepository alarmRepository;
    private final MedicinePerMemberRepository medicinePerMemberRepository;
    private final MedicineRecordRepository medicineRecordRepository;

    private LocalDate START_DATE = LocalDate.now().withDayOfMonth(1);
    private LocalDate END_DATE = LocalDate.now().minusDays(1);

    public MainResponse show(final Long memberId) {
        return MainResponse.builder()
                .medicineAlarmRecords(getMedicineRecords(memberId))
                .grade(validateGrade(getRate(memberId)))
                .takenDay(getTakenDay(memberId))
                .month(getMonth())
                .rate(getRate(memberId))
                .bestRecord(getBestRecord(memberId))
                .worstRecord(getWorstRecord(memberId))
                .build();
    }

    private Integer getTakenDay(Long memberId) {
        return medicineRecordRepository.countEatenDates(memberId, START_DATE, END_DATE);
    }

    private Integer getMonth() {
        return YearMonth.now().lengthOfMonth();
    }

    private Integer getRate(Long memberId) {
        Integer uneatenDays = medicineRecordRepository.countUneatenDays(memberId, START_DATE, END_DATE);
        return 100-(Math.round((100/(getMonth()))* uneatenDays));
    }

    private List<MedicineAlarmRecord> getMedicineRecords(Long memberId) {
        List<Alarm> alarmList = alarmRepository.findAllByMemberId(memberId);
        return alarmList.stream()
                .map(alarm -> MedicineAlarmRecord.builder()
                        .name(alarm.getMedicine().getName())
                        .category(validateCategory(alarm.getMedicine().getCategory()))
                        .time(alarm.getTime())
                        .isEaten(alarm.getIsEaten())
                        .build())
                .collect(Collectors.toList());
    }

    private Integer getTotalAmount(MedicinePerMember medicinePerMember) {
        return medicinePerMember.getAmount() * medicinePerMember.getTimes() * (medicinePerMember.getMonth() * 30 + medicinePerMember.getDate());
    }

    private Integer getTakenAmount(Long memberId, Long medicineId) {
        return medicineRecordRepository.countByMemberIdAndMedicineIdAndIsEatenTrue(memberId, medicineId);
    }

    public List<AdherenceRate> getAllMedicineAdherenceRates(Long memberId) {
        List<MedicinePerMember> medicinePerMembers = medicinePerMemberRepository.findAllByMemberId(memberId);

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
    }

    public BestRecord getBestRecord(Long memberId) {
        return BestRecord.from(getAllMedicineAdherenceRates(memberId).stream().findFirst().orElse(null));
    }

    public WorstRecord getWorstRecord(Long memberId) {
        List<AdherenceRate> rates = getAllMedicineAdherenceRates(memberId);
        return WorstRecord.from(rates.isEmpty() ? null : rates.get(rates.size() - 1));
    }

    private String validateGrade(Integer rate) {
        if (rate >= 95) {
            return "매우 우수";
        } else if (rate >= 90) {
            return "우수";
        } else if (rate >= 70) {
            return "보통";
        } else if (rate >= 50) {
            return "나쁨";
        } else {
            return "매우 나쁨";
        }
    }

    private String validateCategory(String category) {
        if (category.equals("혈압강하제")) {
            return "고혈압";
        } else if (category.equals("동맥경화용제")) {
            return "고지혈증";
        } else if (category.equals("당뇨병용제")) {
            return "당뇨";
        } else if (category.equals("기타의 호흡기관용약")) {
            return "호흡기질환";
        } else {
            return "기타";
        }
    }
}
