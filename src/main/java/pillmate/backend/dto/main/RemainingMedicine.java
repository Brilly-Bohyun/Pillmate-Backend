package pillmate.backend.dto.main;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RemainingMedicine {
    private String name;
    private String category;
    private Long day;
}
