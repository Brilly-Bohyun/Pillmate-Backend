package pillmate.backend.dto.diary;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class EditDiaryRequest {
    private List<String> symptom;

    private Integer score;

    private String record;
}
