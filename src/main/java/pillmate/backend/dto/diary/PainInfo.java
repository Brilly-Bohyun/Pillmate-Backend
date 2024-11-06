package pillmate.backend.dto.diary;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Builder
@Data
public class PainInfo {
    private LocalDate date;
    private Integer level;
}
