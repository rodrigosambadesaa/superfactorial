# Parallel Superfactorial

Java 17/Eclipse project with two complementary implementations of the superfactorial:

- exact `BigInteger` calculation for non-negative integers;
- certified arbitrary-precision calculation for decimal and complex arguments.

The project uses the convention:

```text
S(n) = 1! × 2! × 3! × ... × n!
```

## Exact integer implementation

The Java API accepts and returns `BigInteger`:

```java
BigInteger result = ParallelSuperfactorial.superfactorial(n);
```

For small inputs it accumulates consecutive factorials. For larger practical inputs it calculates the exponent of every prime and multiplies the prime powers through a weight-balanced parallel product tree using `ForkJoinPool`.

Run:

```text
programas.ParallelSuperfactorial
```

## Certified decimal and complex implementation

The analytic continuation is:

```text
S(z) = G(z + 2)
```

where `G` is the Barnes G-function. For every non-negative integer this agrees with the finite product because `G(n + 2) = 1! × 2! × ... × n!`.

The Java front end preserves the decimal input as text and sends its exact rational value to FLINT/Arb. Arb evaluates Barnes G using arbitrary-precision complex ball arithmetic. A result is returned only when both endpoints of the rigorous interval round to the same requested decimal value.

The requested precision is a positive `long`; there is no small fixed precision ceiling in the application. Actual calculations remain bounded by memory, execution time and the limits of the JVM, Python and FLINT data structures.

API example:

```java
ComplexSuperfactorial.CertifiedResult result =
        ComplexSuperfactorial.superfactorial("1.1i", 100_000L);

String real = result.real();
String imaginary = result.imaginary();
long certifiedRealBits = result.realAccuracyBits();
String checksum = result.sha256();
```

The decimal components are returned as strings so that even a precision greater than the `BigDecimal`/`MathContext` `int` limit is not truncated by the Java API. Convenience methods convert them to `BigDecimal` when the requested size fits Java's practical limits.

### Command-line calculation

```text
programas.ComplexSuperfactorial 1.1i 100000 superfactorial-1.1i-100000.txt
```

Accepted input forms include:

```text
3.5
2i
-i
3.5+2.25i
3.5-2.25i
1e-20+2e-5i
```

The parser never converts through `double`. The output file contains the real and imaginary components, interval accuracy in bits, execution time and the SHA-256 digest of the canonical payload `real + "\n" + imaginary + "\n"`.

Both decimal point and decimal comma are accepted, so `1.1i` and `1,1i` are equivalent inputs.

## Requirements

- Java 17 or newer
- Python 3
- Eclipse IDE with Java Development Tools and Maven Integration for Eclipse (`m2e`)
- Maven, when building outside Eclipse

Install the certified numerical backend once:

```bash
python3 -m pip install -r requirements.txt
```

`requirements.txt` pins `python-flint`, whose wheel supplies the FLINT/Arb arbitrary-precision native libraries. Set the `PYTHON` environment variable if the desired Python executable is not named `python3` or `python`.

## Import into Eclipse

1. Open **File -> Import**.
2. Select **Maven -> Existing Maven Projects**.
3. Select the cloned repository as the root directory.
4. Choose `superfactorial` and finish the import.
5. If necessary, select **Maven -> Update Project** from the project's context menu.
6. Install `requirements.txt` in the Python environment visible to Eclipse.
7. Run either main class as a Java application.

## Numerical verification

The certified backend adds 64 decimal guard digits. It then checks that the lower and upper endpoints of each Arb interval produce the same requested significant-digit decimal string. The result also carries Arb's relative accuracy in bits and a SHA-256 integrity digest, which the Java process recalculates before accepting the result.

This is stronger than merely running the same floating-point algorithm twice: every accepted output is enclosed by a rigorously propagated interval.

The repository includes the complete [certified 100,000-digit result for `S(1.1i)`](validation/superfactorial-1.1i-100000.txt) and its [validation certificate](validation/README.md).
