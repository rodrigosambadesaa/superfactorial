package programas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeSuperfactorialTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void nativeBinaryMatchesPureJava() throws Exception {
        assumeTrue(NativeSuperfactorial.isAvailable());
        BigInteger nativeValue = NativeSuperfactorial.calculate(1_000).value();
        assertEquals(ParallelSuperfactorial.superfactorial(BigInteger.valueOf(999))
                        .multiply(factorial(1_000)), nativeValue);
    }

    @Test
    void decimalFileIsVerified() throws Exception {
        assumeTrue(NativeSuperfactorial.isAvailable());
        Path output = temporaryDirectory.resolve("super-1000.txt");
        NativeSuperfactorial.DecimalFileResult result =
                NativeSuperfactorial.writeDecimal(1_000, output);
        assertEquals(result.digits(), Files.size(output));
        assertTrue(result.sha256().matches("[0-9a-f]{64}"));
    }

    private static BigInteger factorial(int n) {
        BigInteger value = BigInteger.ONE;
        for (int k = 2; k <= n; k++) {
            value = value.multiply(BigInteger.valueOf(k));
        }
        return value;
    }
}
