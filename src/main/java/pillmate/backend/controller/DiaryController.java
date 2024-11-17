package pillmate.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pillmate.backend.common.util.LoggedInMember;
import pillmate.backend.dto.diary.CreateDiaryRequest;
import pillmate.backend.dto.diary.CreateDiaryResponse;
import pillmate.backend.dto.diary.EditDiaryRequest;
import pillmate.backend.dto.diary.ShowDiaryResponse;
import pillmate.backend.dto.diary.Today;
import pillmate.backend.entity.member.Symptom;
import pillmate.backend.service.DiaryService;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diaries")
public class DiaryController {
    private final DiaryService diaryService;

    @PostMapping
    public CreateDiaryResponse create(@LoggedInMember Long memberId, @RequestBody CreateDiaryRequest createDiaryRequest) {
        return diaryService.create(memberId, createDiaryRequest);
    }

    @PatchMapping("/{diaryId}")
    public ResponseEntity<String> edit(@PathVariable("diaryId") Long diaryId, @RequestBody EditDiaryRequest editDiaryRequest) {
        diaryService.edit(diaryId, editDiaryRequest);
        return ResponseEntity.ok("건강 일지 수정이 완료 되었습니다.");
    }

    @GetMapping("/{date}")
    public Today showOneDay(@LoggedInMember Long memberId, @PathVariable("date") LocalDate date) {
        return diaryService.show(memberId, date);
    }

    @GetMapping
    public ShowDiaryResponse showMonthly(@LoggedInMember Long memberId, @RequestParam("date") LocalDate date) {
        return diaryService.showMonthly(memberId, date);
    }

    @GetMapping("/symptoms")
    public List<Symptom> showSymptoms(@LoggedInMember Long memberId) {
        return diaryService.showSymptoms(memberId);
    }
}
