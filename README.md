# Parallel Superfactorial

Java 17 calculator with two complementary implementations of the superfactorial:

- an exact `BigInteger` implementation for non-negative integers;
- an arbitrary-precision continuation for real and complex decimal arguments.

This project uses the convention:

```text
S(n) = 1! × 2! × 3! × ... × n!
```

## Exact integer implementation

The integer API accepts and returns `BigInteger`:

```java
BigInteger result = ParallelSuperfactorial.superfactorial(n);
```

For small inputs it accumulates consecutive factorials. For larger practical inputs it calculates the exponent of every prime and multiplies the prime powers through a weight-balanced parallel product tree using `ForkJoinPool`.

Run:

```text
programas.ParallelSuperfactorial
```

## Arbitrary-precision real and complex implementation

The analytic continuation is expressed with the Barnes G-function:

```text
S(z) = G(z + 2)
```

For every non-negative integer this agrees with the finite product because `G(n + 2) = 1! × 2! × ... × n!`.

The API receives a `BigComplex` and an explicit decimal precision:

```java
MathContext context = new MathContext(200, RoundingMode.HALF_EVEN);
BigComplex z = BigComplex.valueOf(
        BigDecimal.ZERO,
        BigDecimalMath.toBigDecimal("1.111111111111111111111111111111111111111")
);

BigComplex result = ComplexSuperfactorial.superfactorial(z, context);
```

Run:

```text
programas.ComplexSuperfactorial
```

Accepted console formats include:

```text
3.5
2i
-i
3.5+2.25i
3.5-2.25i
1e-20+2e-5i
```

The parser never converts through `double`. Long decimal components are read with `BigDecimalMath.toBigDecimal(String)`.

### Numerical method

- `BigComplex`, elementary complex functions and arbitrary-precision decimal operations come from `ch.obermuhlner:big-math:2.3.2`.
- `LogBarnesG` uses the functional equation of the Barnes G-function and an asymptotic expansion.
- `LogGamma` is evaluated by recurrence and Stirling's expansion.
- Bernoulli numbers use an incremental Akiyama-Tanigawa cache with extended internal precision.
- The Kinkelin constant `zeta'(-1)` is calculated dynamically at the working precision.
- The continuation is entire and has zeros at `z = -2, -3, -4, ...`.

## Requirements

- Java 17 or newer
- Eclipse IDE with Java Development Tools and Maven Integration for Eclipse (`m2e`)
- Maven, when building outside Eclipse

The external dependency is declared in `pom.xml` and downloaded from Maven Central.

## Import into Eclipse

1. Open **File -> Import**.
2. Select **Maven -> Existing Maven Projects**.
3. Select the cloned repository as the root directory.
4. Choose `superfactorial` and finish the import.
5. If necessary, select **Maven -> Update Project** from the project's context menu.
6. Run either main class as a Java application.

## Precision and practical limits

"Arbitrary precision" means that the caller chooses a finite number of significant decimal digits. A transcendental complex result normally has infinitely many non-repeating digits, so no program can return all of them.

Input components are not restricted to `double` precision. Java still imposes practical limits through available memory, execution time, `BigDecimal` scale, array sizes and the positive `int` precision used by `MathContext`.
