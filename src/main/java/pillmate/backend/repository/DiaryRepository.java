package pillmate.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pillmate.backend.entity.Diary;

import java.time.LocalDate;
import java.util.List;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    Diary findByMemberIdAndAndDate(Long memberId, LocalDate date);

    @Query("SELECT d FROM Diary d WHERE d.member.id = :memberId AND d.date BETWEEN :startDate AND :endDate")
    List<Diary> findDiariesByMemberIdAndDateRange(@Param("memberId") Long memberId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
}
