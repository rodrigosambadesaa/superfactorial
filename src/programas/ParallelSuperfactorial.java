package programas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Exact arbitrary-precision superfactorial calculator.
 *
 * <pre>
 * S(n) = 1! * 2! * 3! * ... * n!
 *      = 1^n * 2^(n-1) * ... * n^1
 * </pre>
 */
public final class ParallelSuperfactorial {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.TWO;
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private static final int SMALL_INPUT_LIMIT = 250;
    private static final int PRIME_LEAF_SIZE = 32;
    private static final BigInteger BIG_RANGE_LEAF_SIZE = BigInteger.valueOf(8);
    private static final int OUTPUT_WIDTH = 80;
    private static final double LOG2_E = 1.0 / Math.log(2.0);

    private static final ForkJoinPool POOL = ForkJoinPool.commonPool();

    private ParallelSuperfactorial() {
        // Utility class
    }

    /**
     * Interactive entry point for the exact integer implementation.
     *
     * @param args unused
     * @throws IOException if writing the result fails
     */
    public static void main(String[] args) throws IOException {
        System.out.println("=== EXACT PARALLEL SUPERFACTORIAL ===");
        System.out.println("S(n) = 1! × 2! × ... × n!");
        System.out.println("Type q to quit.");

        BufferedWriter output = new BufferedWriter(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
                1 << 20
        );

        try (Scanner keyboard = new Scanner(System.in)) {
            while (true) {
                System.out.print(System.lineSeparator() + "Enter a non-negative integer: ");

                if (!keyboard.hasNext()) {
                    System.out.println(System.lineSeparator() + "Program terminated.");
                    return;
                }

                String token = keyboard.next();
                if (token.equalsIgnoreCase("q")) {
                    System.out.println("Program terminated.");
                    return;
                }

                final BigInteger number;
                try {
                    number = new BigInteger(token);
                } catch (NumberFormatException e) {
                    System.out.println("Error: you must enter a valid integer.");
                    continue;
                }

                try {
                    long calculationStart = System.nanoTime();
                    BigInteger result = superfactorial(number);
                    long calculationEnd = System.nanoTime();

                    long conversionStart = System.nanoTime();
                    String decimalResult = result.toString();
                    long conversionEnd = System.nanoTime();

                    output.write(System.lineSeparator());
                    output.write("Superfactorial of ");
                    output.write(number.toString());
                    output.write(':');
                    output.write(System.lineSeparator());

                    long outputStart = System.nanoTime();
                    writeWrapped(decimalResult, OUTPUT_WIDTH, output);
                    output.flush();
                    long outputEnd = System.nanoTime();

                    System.out.printf(
                            "%nCalculation time: %.3f s%n",
                            nanosToSeconds(calculationEnd - calculationStart)
                    );
                    System.out.printf(
                            "Decimal conversion time: %.3f s%n",
                            nanosToSeconds(conversionEnd - conversionStart)
                    );
                    System.out.printf(
                            "Console output time: %.3f s%n",
                            nanosToSeconds(outputEnd - outputStart)
                    );
                    System.out.printf(
                            "Total measured time: %.3f s%n",
                            nanosToSeconds(outputEnd - calculationStart)
                    );
                    System.out.println("Number of digits: " + decimalResult.length());
                } catch (IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (ArithmeticException e) {
                    System.err.println("Error: the requested result exceeds a JVM arithmetic limit.");
                } catch (OutOfMemoryError e) {
                    System.err.println("Error: not enough JVM heap memory.");
                    System.err.println("Use a larger -Xmx value or a much smaller input.");
                    return;
                }
            }
        }
    }

    /**
     * Calculates {@code S(n) = 1! * 2! * ... * n!} exactly.
     *
     * @param n non-negative integer
     * @return exact superfactorial of {@code n}
     */
    public static BigInteger superfactorial(BigInteger n) {
        validateArgument(n);

        if (n.compareTo(ONE) <= 0) {
            return ONE;
        }

        if (n.compareTo(BigInteger.valueOf(SMALL_INPUT_LIMIT)) <= 0) {
            return sequentialSuperfactorial(n.intValueExact());
        }

        if (n.compareTo(INT_MAX) <= 0) {
            return superfactorialByPrimes(n.intValueExact());
        }

        return POOL.invoke(new BigRangeProductTask(TWO, n, n));
    }

    private static void validateArgument(BigInteger n) {
        if (n == null) {
            throw new IllegalArgumentException("The argument cannot be null");
        }
        if (n.signum() < 0) {
            throw new IllegalArgumentException("Argument must be non-negative");
        }
    }

    private static BigInteger sequentialSuperfactorial(int n) {
        BigInteger factorial = ONE;
        BigInteger result = ONE;

        for (int value = 2; value <= n; value++) {
            factorial = factorial.multiply(BigInteger.valueOf(value));
            result = result.multiply(factorial);
        }

        return result;
    }

    private static BigInteger superfactorialByPrimes(int n) {
        int[] primes = primesUpTo(n);
        PrimeData data = preparePrimeData(n, primes);
        return POOL.invoke(new PrimeProductTask(data, 0, primes.length));
    }

    private static int[] primesUpTo(int limit) {
        if (limit < 2) {
            return new int[0];
        }

        boolean[] compositeOdd = new boolean[(limit >>> 1) + 1];
        int squareRoot = (int) Math.sqrt(limit);

        for (int prime = 3; prime <= squareRoot; prime += 2) {
            if (!compositeOdd[prime >>> 1]) {
                long step = (long) prime << 1;
                for (long multiple = (long) prime * prime;
                     multiple <= limit;
                     multiple += step) {
                    compositeOdd[(int) (multiple >>> 1)] = true;
                }
            }
        }

        int count = 1;
        for (int value = 3; value > 0 && value <= limit; value += 2) {
            if (!compositeOdd[value >>> 1]) {
                count++;
            }
        }

        int[] primes = new int[count];
        primes[0] = 2;
        int index = 1;

        for (int value = 3; value > 0 && value <= limit; value += 2) {
            if (!compositeOdd[value >>> 1]) {
                primes[index++] = value;
            }
        }

        return primes;
    }

    private static PrimeData preparePrimeData(int n, int[] primes) {
        BigInteger[] exponents = new BigInteger[primes.length];
        double[] prefixWeights = new double[primes.length + 1];

        for (int index = 0; index < primes.length; index++) {
            int prime = primes[index];
            BigInteger exponent = exponentInSuperfactorial(n, prime);
            exponents[index] = exponent;

            double bitWeight = Math.max(
                    1.0,
                    exponent.doubleValue() * Math.log(prime) * LOG2_E
            );
            prefixWeights[index + 1] = prefixWeights[index] + bitWeight;
        }

        return new PrimeData(primes, exponents, prefixWeights);
    }

    /**
     * Calculates the exponent of {@code prime} in the superfactorial.
     *
     * <pre>
     * E_p(S(n)) = sum_j sum_(k=1)^n floor(k / p^j)
     * </pre>
     */
    private static BigInteger exponentInSuperfactorial(int n, int prime) {
        BigInteger exponent = BigInteger.ZERO;
        long power = prime;

        while (power <= n) {
            long quotient = n / power;
            long remainder = n % power;

            BigInteger bigQuotient = BigInteger.valueOf(quotient);
            BigInteger blockSum = BigInteger.valueOf(power)
                    .multiply(bigQuotient)
                    .multiply(bigQuotient.subtract(ONE))
                    .shiftRight(1);
            BigInteger tail = bigQuotient.multiply(BigInteger.valueOf(remainder + 1));
            exponent = exponent.add(blockSum).add(tail);

            if (power > n / prime) {
                break;
            }
            power *= prime;
        }

        return exponent;
    }

    /**
     * Raises a {@link BigInteger} to an arbitrary-size non-negative exponent.
     */
    public static BigInteger pow(BigInteger base, BigInteger exponent) {
        if (base == null || exponent == null) {
            throw new IllegalArgumentException("Base and exponent cannot be null");
        }
        if (exponent.signum() < 0) {
            throw new IllegalArgumentException("Exponent must be non-negative");
        }
        if (exponent.compareTo(INT_MAX) <= 0) {
            return base.pow(exponent.intValueExact());
        }

        BigInteger result = ONE;
        BigInteger factor = base;
        BigInteger remaining = exponent;

        while (remaining.signum() != 0) {
            if (remaining.testBit(0)) {
                result = result.multiply(factor);
            }
            remaining = remaining.shiftRight(1);
            if (remaining.signum() != 0) {
                factor = factor.multiply(factor);
            }
        }

        return result;
    }

    private static final class PrimeData {
        private final int[] primes;
        private final BigInteger[] exponents;
        private final double[] prefixWeights;

        private PrimeData(int[] primes, BigInteger[] exponents, double[] prefixWeights) {
            this.primes = primes;
            this.exponents = exponents;
            this.prefixWeights = prefixWeights;
        }
    }

    private static final class PrimeProductTask extends RecursiveTask<BigInteger> {
        private static final long serialVersionUID = 1L;

        private final PrimeData data;
        private final int from;
        private final int to;

        private PrimeProductTask(PrimeData data, int from, int to) {
            this.data = data;
            this.from = from;
            this.to = to;
        }

        @Override
        protected BigInteger compute() {
            int length = to - from;
            if (length <= 0) {
                return ONE;
            }
            if (length <= PRIME_LEAF_SIZE) {
                BigInteger result = ONE;
                for (int index = from; index < to; index++) {
                    result = result.multiply(pow(
                            BigInteger.valueOf(data.primes[index]),
                            data.exponents[index]
                    ));
                }
                return result;
            }

            int middle = weightedMidpoint(data.prefixWeights, from, to);
            if (middle <= from || middle >= to) {
                middle = from + (length >>> 1);
            }

            PrimeProductTask left = new PrimeProductTask(data, from, middle);
            PrimeProductTask right = new PrimeProductTask(data, middle, to);
            left.fork();
            BigInteger rightResult = right.compute();
            return left.join().multiply(rightResult);
        }
    }

    private static int weightedMidpoint(double[] prefix, int from, int to) {
        double target = prefix[from] + (prefix[to] - prefix[from]) / 2.0;
        int low = from + 1;
        int high = to - 1;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (prefix[middle] < target) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return Math.min(Math.max(low, from + 1), to - 1);
    }

    private static final class BigRangeProductTask extends RecursiveTask<BigInteger> {
        private static final long serialVersionUID = 1L;

        private final BigInteger start;
        private final BigInteger end;
        private final BigInteger n;

        private BigRangeProductTask(BigInteger start, BigInteger end, BigInteger n) {
            this.start = start;
            this.end = end;
            this.n = n;
        }

        @Override
        protected BigInteger compute() {
            if (start.compareTo(end) > 0) {
                return ONE;
            }

            BigInteger length = end.subtract(start).add(ONE);
            if (length.compareTo(BIG_RANGE_LEAF_SIZE) <= 0) {
                BigInteger result = ONE;
                for (BigInteger value = start;
                     value.compareTo(end) <= 0;
                     value = value.add(ONE)) {
                    BigInteger exponent = n.subtract(value).add(ONE);
                    result = result.multiply(pow(value, exponent));
                }
                return result;
            }

            BigInteger middle = start.add(end).shiftRight(1);
            BigRangeProductTask left = new BigRangeProductTask(start, middle, n);
            BigRangeProductTask right = new BigRangeProductTask(middle.add(ONE), end, n);
            left.fork();
            BigInteger rightResult = right.compute();
            return left.join().multiply(rightResult);
        }
    }

    private static void writeWrapped(String text, int width, BufferedWriter output)
            throws IOException {
        for (int start = 0; start < text.length(); start += width) {
            int length = Math.min(width, text.length() - start);
            output.write(text, start, length);
            output.write(System.lineSeparator());
        }
    }

    private static double nanosToSeconds(long nanoseconds) {
        return nanoseconds / 1_000_000_000.0;
    }
}
