import org.junit.jupiter.api.extension.ExtendWith;
import org.pastalab.fray.junit.junit5.FrayTestExtension;
import org.pastalab.fray.junit.junit5.annotations.ConcurrencyTest;

@ExtendWith(FrayTestExtension.class)
public class Deadlock01BadTest {
    @ConcurrencyTest(iterations = 100)
    public void testDeadlock() throws InterruptedException {
        Deadlock01Bad.main(new String[]{});
    }
}
