package pillmate.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@Builder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alarm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "medicine_per_member_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private MedicinePerMember medicinePerMember;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "timeSlot_id")
    private TimeSlot timeSlot;

    @Builder.Default
    @Column(name = "isEaten", nullable = false)
    private Boolean isEaten = FALSE;

    @Builder.Default
    @Column(name = "isAvailable", nullable = false)
    private Boolean isAvailable = TRUE;

    @Builder
    public Alarm(Long id, MedicinePerMember medicinePerMember, TimeSlot timeSlot, Boolean isEaten, Boolean isAvailable) {
        this.id = id;
        this.medicinePerMember = medicinePerMember;
        this.timeSlot = timeSlot;
        this.isEaten = isEaten;
        this.isAvailable = isAvailable;
    }

    public void updateTimeSlot(TimeSlot newTimeSlot) {
        if (newTimeSlot != null) {
            this.timeSlot = newTimeSlot;
        }
    }

    public void updateAvailability(Boolean isAvailable) {
        if (isAvailable != null) {
            this.isAvailable = isAvailable;
        }
    }

    public void updateIsEaten(Boolean isEaten) {
        if (isEaten != null) {
            this.isEaten = isEaten;
        }
    }
}
