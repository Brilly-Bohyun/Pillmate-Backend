package pillmate.backend.dto.medicine;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Builder
@Data
public class UpcomingAlarm {
    private String medicineName;
    private String category;
    private LocalTime time;
}
