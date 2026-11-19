package ua.edu.ukma.community.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BoardScheduler")
class BoardSchedulerTest {

    @Test
    @DisplayName("кожен запуск закриває прострочені оголошення через сервіс")
    void delegatesToService() {
        BoardService board = mock(BoardService.class);
        when(board.closeExpired()).thenReturn(2, 0);
        BoardScheduler scheduler = new BoardScheduler(board);

        scheduler.closeExpired();
        scheduler.closeExpired();

        verify(board, times(2)).closeExpired();
    }
}
