package pillmate.backend.dto.alarm;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Builder
@Data
public class SimpleAlarmInfo {
    private String name;
    private String category;
    private LocalTime time;
}
