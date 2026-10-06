package programas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import ch.obermuhlner.math.big.BigComplex;
import ch.obermuhlner.math.big.BigComplexMath;
import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * Arbitrary-precision logarithm of the Barnes G-function.
 *
 * <p>The implementation shifts the argument into the right half-plane and
 * evaluates the standard asymptotic expansion. Bernoulli numbers and
 * zeta'(-1) are calculated at the requested working precision, so the result
 * is not capped by an embedded decimal constant.</p>
 */
final class BarnesG {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private static final int GUARD_DIGITS = 24;
    private static final int MAX_SHIFTS = 1_000_000;
    private static final int MAX_SERIES_TERMS = 1_000_000;

    private BarnesG() {
        // Utility class
    }

    /**
     * Calculates the principal logarithm of {@code G(argument)}.
     *
     * @param argument Barnes G argument
     * @param resultContext requested result precision
     * @return logarithm of the Barnes G-function
     */
    static BigComplex log(BigComplex argument, MathContext resultContext) {
        requireFinitePrecision(resultContext);
        rejectZero(argument);

        MathContext workContext = new MathContext(
                Math.addExact(resultContext.getPrecision(), GUARD_DIGITS),
                resultContext.getRoundingMode()
        );

        int targetRealPart = Math.max(
                24,
                (workContext.getPrecision() + 30) / 2 + 10
        );

        BigDecimal requiredShift = BigDecimal.valueOf(targetRealPart)
                .subtract(argument.re)
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.CEILING);

        final int shifts;
        try {
            shifts = requiredShift.intValueExact();
        } catch (ArithmeticException e) {
            throw new ArithmeticException(
                    "The real part is too negative for recurrence-based Barnes G evaluation"
            );
        }

        if (shifts > MAX_SHIFTS) {
            throw new ArithmeticException(
                    "Barnes G evaluation would require more than " + MAX_SHIFTS + " recurrence steps"
            );
        }

        BigComplex shiftedArgument = argument.add(BigDecimal.valueOf(shifts), workContext);
        BigComplex result = asymptoticLog(shiftedArgument, workContext);
        BigComplex currentLogGamma = ComplexLogGamma.log(argument, workContext);

        for (int index = 0; index < shifts; index++) {
            result = result.subtract(currentLogGamma, workContext);

            BigComplex recurrenceFactor = argument.add(BigDecimal.valueOf(index), workContext);
            currentLogGamma = currentLogGamma.add(
                    BigComplexMath.log(recurrenceFactor, workContext),
                    workContext
            );
        }

        return result.round(resultContext);
    }

    private static BigComplex asymptoticLog(BigComplex argument, MathContext context) {
        BigComplex z = argument.subtract(BigComplex.ONE, context);
        BigComplex logZ = BigComplexMath.log(z, context);
        BigComplex zSquared = z.multiply(z, context);

        BigDecimal pi = BigDecimalMath.pi(context);
        BigDecimal logTwoPi = BigDecimalMath.log(pi.multiply(TWO, context), context);

        BigComplex result = zSquared.multiply(logZ, context).divide(TWO, context)
                .subtract(zSquared.multiply(THREE, context).divide(FOUR, context), context)
                .add(z.multiply(logTwoPi, context).divide(TWO, context), context)
                .subtract(logZ.divide(TWELVE, context), context)
                .add(BigComplex.valueOf(zetaPrimeMinusOne(context)), context);

        BigComplex inverseZSquared = BigComplex.ONE.divide(zSquared, context);
        BigComplex inversePower = inverseZSquared;
        BigDecimal previousMagnitudeSquared = null;
        BigDecimal toleranceSquared = BigDecimal.ONE.scaleByPowerOfTen(
                Math.multiplyExact(-2, context.getPrecision())
        );

        double absoluteValue = z.abs(context).doubleValue();
        if (!Double.isFinite(absoluteValue)) {
            throw new ArithmeticException("The Barnes G argument is too large to plan the asymptotic series");
        }

        int maximumTerms = Math.min(
                MAX_SERIES_TERMS,
                Math.max(50, (int) Math.ceil(Math.PI * absoluteValue) + 50)
        );

        boolean converged = false;

        for (int k = 1; k <= maximumTerms; k++) {
            int bernoulliIndex = Math.addExact(Math.multiplyExact(2, k), 2);
            BigDecimal bernoulli = BernoulliNumbers.value(bernoulliIndex, context);
            BigDecimal denominator = BigDecimal.valueOf(2L * k)
                    .multiply(BigDecimal.valueOf(2L * k + 2), context);
            BigDecimal coefficient = bernoulli.divide(denominator, context);
            BigComplex term = inversePower.multiply(coefficient, context);
            BigDecimal magnitudeSquared = term.absSquare(context);

            if (previousMagnitudeSquared != null
                    && magnitudeSquared.compareTo(previousMagnitudeSquared) >= 0) {
                break;
            }

            result = result.add(term, context);

            if (magnitudeSquared.compareTo(toleranceSquared) < 0) {
                converged = true;
                break;
            }

            previousMagnitudeSquared = magnitudeSquared;
            inversePower = inversePower.multiply(inverseZSquared, context);
        }

        if (!converged) {
            throw new ArithmeticException(
                    "The Barnes G asymptotic series did not reach the requested precision"
            );
        }

        return result;
    }

    /**
     * Euler-Maclaurin evaluation of the Kinkelin constant zeta'(-1).
     */
    private static BigDecimal zetaPrimeMinusOne(MathContext context) {
        int n = Math.max(12, (context.getPrecision() + 20) / 2 + 6);
        BigDecimal bigN = BigDecimal.valueOf(n);
        BigDecimal logN = BigDecimalMath.log(bigN, context);

        BigInteger finiteHyperfactorial = BigInteger.ONE;
        for (int k = 2; k < n; k++) {
            finiteHyperfactorial = finiteHyperfactorial.multiply(
                    BigInteger.valueOf(k).pow(k)
            );
        }

        BigDecimal result = BigDecimalMath.log(
                new BigDecimal(finiteHyperfactorial),
                context
        ).negate();

        BigDecimal nSquared = bigN.multiply(bigN, context);
        result = result
                .add(
                        nSquared.divide(TWO, context)
                                .subtract(bigN.divide(TWO, context), context)
                                .multiply(logN, context),
                        context
                )
                .subtract(nSquared.divide(FOUR, context), context)
                .add(logN.add(BigDecimal.ONE, context).divide(TWELVE, context), context);

        BigDecimal inverseNSquared = BigDecimal.ONE.divide(nSquared, context);
        BigDecimal inversePower = inverseNSquared;
        BigDecimal previousMagnitude = null;
        BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-context.getPrecision());
        int maximumTerms = Math.min(
                MAX_SERIES_TERMS,
                Math.max(50, (int) Math.ceil(Math.PI * n) + 50)
        );

        boolean converged = false;

        for (int r = 2; r <= maximumTerms; r++) {
            int twiceR = Math.multiplyExact(2, r);
            BigDecimal bernoulli = BernoulliNumbers.value(twiceR, context);
            BigDecimal denominator = BigDecimal.valueOf(twiceR)
                    .multiply(BigDecimal.valueOf(twiceR - 1L), context)
                    .multiply(BigDecimal.valueOf(twiceR - 2L), context);

            BigDecimal term = bernoulli.negate()
                    .divide(denominator, context)
                    .multiply(inversePower, context);
            BigDecimal magnitude = term.abs();

            if (previousMagnitude != null && magnitude.compareTo(previousMagnitude) >= 0) {
                break;
            }

            result = result.add(term, context);

            if (magnitude.compareTo(tolerance) < 0) {
                converged = true;
                break;
            }

            previousMagnitude = magnitude;
            inversePower = inversePower.multiply(inverseNSquared, context);
        }

        if (!converged) {
            throw new ArithmeticException("Unable to calculate zeta'(-1) to the requested precision");
        }

        return result;
    }

    private static void rejectZero(BigComplex argument) {
        if (argument.isReal()
                && argument.re.signum() <= 0
                && argument.re.stripTrailingZeros().scale() <= 0) {
            throw new ArithmeticException("Barnes G has a zero at non-positive integers");
        }
    }

    private static void requireFinitePrecision(MathContext context) {
        if (context == null || context.getPrecision() <= 0) {
            throw new IllegalArgumentException(
                    "A finite positive output precision is required for transcendental calculations"
            );
        }
    }
}

