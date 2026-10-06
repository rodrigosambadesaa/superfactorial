package programas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class ParallelSuperfactorialTest {

    @Test
    void matchesSequentialReferenceThroughThreeHundred() {
        BigInteger factorial = BigInteger.ONE;
        BigInteger expected = BigInteger.ONE;
        for (int n = 0; n <= 300; n++) {
            if (n >= 2) {
                factorial = factorial.multiply(BigInteger.valueOf(n));
                expected = expected.multiply(factorial);
            }
            assertEquals(expected,
                    ParallelSuperfactorial.superfactorial(BigInteger.valueOf(n)),
                    "Mismatch at n=" + n);
        }
    }

    @Test
    void rejectsNegativeAndNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> ParallelSuperfactorial.superfactorial(BigInteger.valueOf(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> ParallelSuperfactorial.superfactorial(null));
    }
}
