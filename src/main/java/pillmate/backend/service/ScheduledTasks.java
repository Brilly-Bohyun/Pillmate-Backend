package pillmate.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScheduledTasks {

    @Autowired
    private AlarmService alarmService;

    @Scheduled(cron = "0 0 0 * * ?", zone="Asia/Seoul")
    public void resetIsEatenAtMidnight() {
        alarmService.resetAllIsEaten();
    }
}
