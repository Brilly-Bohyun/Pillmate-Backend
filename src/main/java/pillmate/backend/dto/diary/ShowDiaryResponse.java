package pillmate.backend.dto.diary;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class ShowDiaryResponse {
    private List<PainInfo> painsPerDay;
    private Long duration;
    private List<TotalInfo> totalInfo;
    private Today today;
}
