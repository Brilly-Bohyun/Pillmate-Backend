package pillmate.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pillmate.backend.common.exception.NotFoundException;
import pillmate.backend.common.exception.errorcode.ErrorCode;
import pillmate.backend.dto.alarm.AlarmInfo;
import pillmate.backend.dto.medicine.UpcomingAlarm;
import pillmate.backend.entity.Alarm;
import pillmate.backend.entity.MedicinePerMember;
import pillmate.backend.repository.AlarmRepository;
import pillmate.backend.repository.MedicinePerMemberRepository;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AlarmService {
    private final AlarmRepository alarmRepository;
    private final MedicinePerMemberRepository medicinePerMemberRepository;

    public List<AlarmInfo> showAll(Long memberId) {
        return alarmRepository.findAllByMemberId(memberId).stream()
                .map(alarm -> AlarmInfo.builder()
                        .id(alarm.getId())
                        .name(alarm.getMedicinePerMember().getMedicine().getName())
                        .category(alarm.getMedicinePerMember().getMedicine().getCategory())
                        .amount(alarm.getMedicinePerMember().getAmount())
                        .timesPerDay(alarm.getMedicinePerMember().getTimes())
                        .day(alarm.getMedicinePerMember().getDay())
                        .timeSlot(alarm.getTimeSlot())
                        .isAvailable(alarm.getIsAvailable())
                        .build())
                .sorted(Comparator.comparing(alarmInfo -> alarmInfo.getTimeSlot().getPickerTime())) // pickerTime을 기준으로 정렬
                .collect(Collectors.toList());
    }

    @Transactional
    public ResponseEntity<String> updateAvailability(Long alarmId, Boolean available, Long memberId) {
        findByAlarmId(alarmId).updateAvailability(available);
        return ResponseEntity.ok("알람 on/off 설정이 변경되었습니다.");
    }

    @Transactional
    public void deleteAlarm(Long memberId, String medicineName) {
        findByMemberIdAndMedicineName(memberId, medicineName).forEach(alarm -> alarmRepository.deleteById(alarm.getId()));
    }

    @Transactional
    public void resetAllIsEaten() {
        alarmRepository.updateAllIsEatenToFalse();
    }

    public UpcomingAlarm getUpcomingAlarm(Long memberId, LocalTime currentTime) {
        Alarm upcomingAlarm = alarmRepository.findAllByMemberId(memberId).stream()
                .filter(alarm -> alarm.getIsAvailable().booleanValue() == Boolean.TRUE && alarm.getIsEaten().booleanValue() == Boolean.FALSE)
                .filter(alarm -> alarm.getTimeSlot().getPickerTime().isAfter(currentTime)) // 현재 시간 이후의 알람 필터링
                .sorted(Comparator.comparing(alarm -> alarm.getTimeSlot().getPickerTime())) // pickerTime 기준으로 정렬
                .findFirst() // 현재 시간 이후의 첫 번째 알람을 찾음
                .orElseGet(() ->
                        alarmRepository.findAllByMemberId(memberId).stream() // 현재 시간 이후 알람이 없을 경우
                                .filter(alarm -> Boolean.TRUE.equals(alarm.getIsAvailable()) && Boolean.FALSE.equals(alarm.getIsEaten()))
                                .sorted(Comparator.comparing(alarm -> alarm.getTimeSlot().getPickerTime())) // pickerTime 기준으로 정렬
                                .findFirst() // 다음 날 첫 번째 알람을 찾음
                                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_ALARM))
                );

        return UpcomingAlarm.builder().medicineName(upcomingAlarm.getMedicinePerMember().getMedicine().getName())
                .category(upcomingAlarm.getMedicinePerMember().getMedicine().getCategory())
                .time(upcomingAlarm.getTimeSlot().getPickerTime()).build();
    }

    private MedicinePerMember findByMemberIdAndMedicineId(Long memberId, Long medicineId) {
        return medicinePerMemberRepository.findByMemberIdAndMedicineId(memberId, medicineId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_MEDICINE_MEMBER));
    }

    private Alarm findByAlarmId(Long alarmId) {
        return alarmRepository.findById(alarmId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_ALARM));
    }

    private List<Alarm> findByMemberIdAndMedicineName(Long memberId, String medicineName) {
        return alarmRepository.findAllByMemberIdAndMedicineName(memberId, medicineName);
    }
}
