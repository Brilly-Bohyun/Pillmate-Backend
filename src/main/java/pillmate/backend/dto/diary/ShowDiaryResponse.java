package pillmate.backend.dto.diary;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class ShowDiaryResponse {
    private MonthlyScore monthlyScore;
    private Today today;
}
