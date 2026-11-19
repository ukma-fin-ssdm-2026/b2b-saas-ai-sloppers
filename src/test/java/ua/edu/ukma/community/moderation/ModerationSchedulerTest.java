package ua.edu.ukma.community.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ModerationScheduler")
class ModerationSchedulerTest {

    @Test
    @DisplayName("кожен запуск знімає прострочені бани через сервіс")
    void delegatesToService() {
        ModerationService moderation = mock(ModerationService.class);
        when(moderation.liftExpiredBans()).thenReturn(1, 0);
        ModerationScheduler scheduler = new ModerationScheduler(moderation);

        scheduler.liftExpiredBans();
        scheduler.liftExpiredBans();

        verify(moderation, times(2)).liftExpiredBans();
    }
}
