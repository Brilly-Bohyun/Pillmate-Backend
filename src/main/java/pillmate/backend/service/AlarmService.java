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
import pillmate.backend.entity.TimeSlot;
import pillmate.backend.repository.AlarmRepository;
import pillmate.backend.repository.MedicinePerMemberRepository;

import java.time.LocalDate;
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
                .filter(alarm -> alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isAfter(LocalDate.now()))
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

    @Transactional
    public void updateTime(Long memberId, String medicineName, List<TimeSlot> timeSlots) {
        // 1. Alarm 리스트 가져오기
        List<Alarm> alarms = findByMemberIdAndMedicineName(memberId, medicineName);
        int currentAlarmCount = alarms.size();
        int newTimeSlotCount = timeSlots.size();

        // 2. Alarm의 개수를 TimeSlot에 맞추기
        if (newTimeSlotCount > currentAlarmCount) {
            // 부족한 알람 개수만큼 새로운 Alarm 추가
            for (int i = currentAlarmCount; i < newTimeSlotCount; i++) {
                Alarm newAlarm = Alarm.builder()
                        .medicinePerMember(alarms.get(0).getMedicinePerMember()) // 기존 MedicinePerMember 사용
                        .timeSlot(timeSlots.get(i))
                        .isEaten(false)
                        .isAvailable(true)
                        .build();
                alarms.add(newAlarm);
            }
        } else if (newTimeSlotCount < currentAlarmCount) {
            // 초과한 알람 제거
            alarms.subList(newTimeSlotCount, currentAlarmCount).clear();
        }

        // 3. 알람의 timeSlot 업데이트
        for (int i = 0; i < newTimeSlotCount; i++) {
            alarms.get(i).updateTimeSlot(timeSlots.get(i));
        }

        // 4. 변경된 알람들 저장
        alarmRepository.saveAll(alarms);
    }

    public UpcomingAlarm getUpcomingAlarm(Long memberId, LocalTime currentTime) {
        Alarm upcomingAlarm = alarmRepository.findAllByMemberId(memberId).stream()
                .filter(alarm -> alarm.getIsAvailable().booleanValue() == Boolean.TRUE && alarm.getIsEaten().booleanValue() == Boolean.FALSE)
                .filter(alarm -> alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isAfter(LocalDate.now())
                        || alarm.getMedicinePerMember().getCreated().plusDays(alarm.getMedicinePerMember().getDay()).isEqual(LocalDate.now()))
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

    private Alarm findByAlarmId(Long alarmId) {
        return alarmRepository.findById(alarmId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_ALARM));
    }

    private List<Alarm> findByMemberIdAndMedicineName(Long memberId, String medicineName) {
        return alarmRepository.findAllByMemberIdAndMedicineName(memberId, medicineName);
    }
}
