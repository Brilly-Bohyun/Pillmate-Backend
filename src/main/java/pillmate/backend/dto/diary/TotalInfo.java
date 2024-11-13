package pillmate.backend.dto.diary;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Builder
@Data
public class TotalInfo {
    private String name;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
}
