package pillmate.backend.dto.diary;

import lombok.Builder;
import lombok.Data;
import pillmate.backend.dto.alarm.SimpleAlarmInfo;

import java.util.List;

@Builder
@Data
public class Today {
    private Long id;
    private List<SimpleAlarmInfo> alarms;
    private List<String> symptoms;
    private Integer score;
    private String comment;
    private String record;
}
