package pillmate.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pillmate.backend.entity.MedicineRecord;

import java.time.LocalDate;
import java.util.List;

public interface MedicineRecordRepository extends JpaRepository<MedicineRecord, Long> {
    @Query("SELECT mr.date " +
            "FROM MedicineRecord mr " +
            "WHERE mr.member.id = :memberId " +
            "AND mr.date BETWEEN :startDate AND :endDate " +
            "AND mr.isEaten = TRUE " +
            "GROUP BY mr.date " +
            "HAVING COUNT(DISTINCT mr.medicine.id) = (SELECT COUNT(m.id) FROM Medicine m)")
    List<LocalDate> countEatenDates(@Param("memberId") Long memberId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(mr) FROM MedicineRecord mr WHERE mr.member.id = :memberId AND mr.medicine.id = :medicineId AND mr.isEaten = true")
    Integer countByMemberIdAndMedicineIdAndIsEatenTrue(@Param("memberId") Long memberId, @Param("medicineId") Long medicineId);

    @Query("SELECT m FROM MedicineRecord m WHERE m.member.id = :memberId AND m.date BETWEEN :startDate AND :currentDate")
    List<MedicineRecord> findAllByMemberAndDateBetween(@Param("memberId") Long memberId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("currentDate") LocalDate currentDate);}
