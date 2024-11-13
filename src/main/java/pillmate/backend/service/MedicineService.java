package pillmate.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pillmate.backend.common.exception.BadRequestException;
import pillmate.backend.common.exception.NotFoundException;
import pillmate.backend.common.exception.errorcode.ErrorCode;
import pillmate.backend.dto.medicine.AddRequest;
import pillmate.backend.dto.medicine.MedicineBasicInfo;
import pillmate.backend.dto.medicine.MedicineInfo;
import pillmate.backend.dto.medicine.ModifyMedicineInfo;
import pillmate.backend.dto.medicine.PrescriptionRequest;
import pillmate.backend.dto.medicine.UpcomingAlarm;
import pillmate.backend.entity.Alarm;
import pillmate.backend.entity.Medicine;
import pillmate.backend.entity.MedicinePerMember;
import pillmate.backend.entity.MedicineRecord;
import pillmate.backend.entity.TimeSlot;
import pillmate.backend.entity.member.Member;
import pillmate.backend.repository.AlarmRepository;
import pillmate.backend.repository.MedicinePerMemberRepository;
import pillmate.backend.repository.MedicineRecordRepository;
import pillmate.backend.repository.MedicineRepository;
import pillmate.backend.repository.MemberRepository;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicineService {
    private final AlarmService alarmService;
    private final AlarmRepository alarmRepository;
    private final MedicineRepository medicineRepository;
    private final MemberRepository memberRepository;
    private final MedicineRecordRepository medicineRecordRepository;
    private final MedicinePerMemberRepository medicinePerMemberRepository;

    @Transactional
    public UpcomingAlarm getUpcomingAlarm(Long memberId, LocalTime currentTime, Long medicineId) {
        Alarm currentAlarm = findByMedicineAndTime(memberId, medicineId, currentTime);
        currentAlarm.updateIsEaten(true);
        medicineRecordRepository.save(MedicineRecord.builder().member(findByMemberId(memberId))
                .medicine(currentAlarm.getMedicinePerMember().getMedicine())
                .time(currentTime)
                .isEaten(true)
                .build());

        return alarmService.getUpcomingAlarm(memberId, currentTime);
    }

    public List<MedicineBasicInfo> getMedicineInfo(Long memberId, List<PrescriptionRequest> nameList) {
        return nameList.stream()
                .map(p -> medicineRepository.findByName(p.getName())
                        .map(m -> MedicineBasicInfo.builder()
                                .name(m.getName())
                                .photo(m.getPhoto())
                                .category(m.getCategory())
                                .build()
                        )
                        .orElse(MedicineBasicInfo.builder()
                                .name(p.getName())
                                .photo("db에서 해당 약을 찾을 수 없습니다")
                                .category("db에서 해당 약을 찾을 수 없습니다")
                                .build())
                )
                .collect(Collectors.toList());
    }

    @Transactional
    public void add(Long memberId, AddRequest addRequest) {
        Optional<Medicine> foundmedicine = medicineRepository.findByName(addRequest.getMedicineName());
        Medicine newMedicine = foundmedicine.orElseGet(() -> medicineRepository.save(Medicine.builder()
                .name(addRequest.getMedicineName())
                .category(addRequest.getDisease())
                .photo("white")
                .build()));

        // Step 2: MedicinePerMember 중복 확인 및 저장
        Member member = findByMemberId(memberId);
        Optional<MedicinePerMember> foundMedicinePerMember = medicinePerMemberRepository
                .findByMemberIdAndMedicineId(memberId, newMedicine.getId());

        // 중복된 MedicinePerMember가 있으면 예외 던지기
        if (foundMedicinePerMember.isPresent()) {
            throw new BadRequestException(ErrorCode.INVALID_SAME_MEDICINE); // 이미 존재하므로 추가 작업 수행하지 않음
        }

        // 중복이 없으면 새로운 MedicinePerMember 및 Alarm 저장
        MedicinePerMember newMedicinePerMember = addRequest.toEntity(member, newMedicine);
        saveMedicinePerMember(newMedicinePerMember);

        // TimeSlot 리스트에서 새로운 알람에 사용할 TimeSlot 선택
        for (TimeSlot existingTimeSlot : newMedicinePerMember.getTimeSlots()) {
            // 새로운 알람 생성 및 저장
            Alarm newAlarm = Alarm.builder()
                    .medicinePerMember(newMedicinePerMember)
                    .timeSlot(existingTimeSlot)
                    .build();

            alarmRepository.save(newAlarm);
        }
    }

    private void saveMedicinePerMember(MedicinePerMember addRequest) {
        medicinePerMemberRepository.save(addRequest);
    }

    public List<MedicineInfo> showAll(Long memberId) {
        return findAllByMemberId(memberId).stream()
                .map(medicinePerMember -> MedicineInfo.builder()
                        .id(medicinePerMember.getMedicine().getId())
                        .picture(medicinePerMember.getMedicine().getPhoto())
                        .name(medicinePerMember.getMedicine().getName())
                        .category(medicinePerMember.getMedicine().getCategory())
                        .amount(medicinePerMember.getAmount())
                        .timesPerDay(medicinePerMember.getTimes())
                        .day(medicinePerMember.getDay())
                        .timeSlotList(medicinePerMember.getTimeSlots())
                        .build())
                .sorted(Comparator.comparing(MedicineInfo::getName))
                .collect(Collectors.toList());
    }

    @Transactional
    public void modify(Long memberId, ModifyMedicineInfo modifyMedicineInfo) {
        Medicine medicine = findByName(modifyMedicineInfo.getOldMedicineName());
        MedicinePerMember medicinePerMember = findByMemberIdAndMedicineId(memberId, medicine.getId());
        medicinePerMember.update(modifyMedicineInfo.getAmount(),
                modifyMedicineInfo.getTimesPerDay(),
                modifyMedicineInfo.getDay(),
                modifyMedicineInfo.getTimeSlotList());
        alarmService.updateTime(memberId, medicinePerMember.getMedicine().getName(), modifyMedicineInfo.getTimeSlotList());
        if ("white".equals(medicine.getPhoto())) {
            medicinePerMember.getMedicine().updateName(modifyMedicineInfo.getNewMedicineName());
        }
    }

    @Transactional
    public void delete(Long memberId, Long medicineId) {
        MedicinePerMember medicinePerMember = findByMemberIdAndMedicineId(memberId, medicineId);
        alarmService.deleteAlarm(memberId, medicinePerMember.getMedicine().getName());
        medicinePerMemberRepository.deleteById(medicinePerMember.getId());
    }

    private List<MedicinePerMember> findAllByMemberId(Long memberId) {
        return medicinePerMemberRepository.findAllByMemberId(memberId);
    }

    private MedicinePerMember findByMemberIdAndMedicineId(Long memberId, Long medicineId) {
        return medicinePerMemberRepository.findByMemberIdAndMedicineId(memberId, medicineId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_MEDICINE_MEMBER));
    }

    private Member findByMemberId(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_MEMBER));
    }

    private Medicine findByName(String name) {
        return medicineRepository.findByName(name).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_MEDICINE));
    }

    private Alarm findByMedicineAndTime(Long memberId, Long medicineId, LocalTime time) {
        return alarmRepository.findByMemberIdAndMedicineIdAndTime(memberId, medicineId, time).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND_ALARM));
    }
}
