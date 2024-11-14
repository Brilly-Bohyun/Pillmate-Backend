package pillmate.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pillmate.backend.entity.MedicinePerMember;

import java.util.List;
import java.util.Optional;

public interface MedicinePerMemberRepository extends JpaRepository<MedicinePerMember, Long> {
    List<MedicinePerMember> findAllByMemberId(Long memberId);
    Optional<MedicinePerMember> findByMemberIdAndMedicineId(Long memberId, Long medicineId);
}
