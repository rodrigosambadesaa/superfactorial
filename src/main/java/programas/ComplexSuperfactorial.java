package programas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Scanner;

/**
 * Certified arbitrary-precision complex continuation of the superfactorial.
 *
 * <pre>
 * S(z) = G(z + 2)
 * </pre>
 *
 * <p>The numerical backend is FLINT/Arb through the bundled Python worker.
 * Arb uses complex ball arithmetic: the returned decimal digits are accepted
 * only when the complete interval rounds to one unique decimal result.</p>
 */
public final class ComplexSuperfactorial {

    private static final String WORKER_RESOURCE = "/certified_superfactorial.py";
    private static final String PYTHON_ENVIRONMENT_VARIABLE = "PYTHON";

    private ComplexSuperfactorial() {
        // Utility class
    }

    /**
     * Command-line and interactive entry point.
     *
     * <p>Non-interactive usage:</p>
     *
     * <pre>
     * ComplexSuperfactorial 1.1i 100000 result.txt
     * </pre>
     *
     * @param args optional input, precision and output path
     * @throws IOException if the worker or output cannot be accessed
     * @throws InterruptedException if the calculation is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length >= 2) {
            long precision = parsePrecision(args[1]);
            CertifiedResult result = superfactorial(args[0], precision);

            if (args.length >= 3) {
                Path output = Path.of(args[2]);
                try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                    writeResult(args[0], result, writer);
                }
                System.out.println("Result written to " + output.toAbsolutePath());
                printCertificate(result);
            } else {
                writeResult(args[0], result, new java.io.OutputStreamWriter(
                        System.out,
                        StandardCharsets.UTF_8
                ));
            }
            return;
        }

        System.out.println("=== CERTIFIED ARBITRARY-PRECISION COMPLEX SUPERFACTORIAL ===");
        System.out.println("S(z) = G(z + 2), evaluated with FLINT/Arb ball arithmetic");
        System.out.println("Accepted examples: 3.5, 2i, -i, 3.5-2.25i, 1e-20+2e-5i");

        try (Scanner keyboard = new Scanner(System.in)) {
            System.out.print("Enter z: ");
            String input = keyboard.nextLine().trim();
            System.out.print("Significant decimal digits: ");
            long precision = parsePrecision(keyboard.nextLine().trim());
            System.out.print("Output file (leave empty for console): ");
            String outputText = keyboard.nextLine().trim();

            CertifiedResult result = superfactorial(input, precision);
            if (outputText.isEmpty()) {
                writeResult(input, result, new java.io.OutputStreamWriter(
                        System.out,
                        StandardCharsets.UTF_8
                ));
            } else {
                Path output = Path.of(outputText);
                try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                    writeResult(input, result, writer);
                }
                System.out.println("Result written to " + output.toAbsolutePath());
                printCertificate(result);
            }
        }
    }

    /**
     * Computes {@code S(z) = G(z + 2)} with a requested number of significant
     * decimal digits and a rigorous enclosure.
     *
     * @param input real or complex decimal input
     * @param precision requested significant decimal digits
     * @return certified decimal components and verification metadata
     */
    public static CertifiedResult superfactorial(String input, long precision)
            throws IOException, InterruptedException {
        if (precision <= 0) {
            throw new IllegalArgumentException("Precision must be positive");
        }

        ComplexInputParser.ParsedComplex parsed = ComplexInputParser.parse(input);
        return superfactorial(parsed.real(), parsed.imaginary(), precision);
    }

    /**
     * Computes the complex superfactorial from separate decimal components.
     */
    public static CertifiedResult superfactorial(
            String real,
            String imaginary,
            long precision
    ) throws IOException, InterruptedException {
        if (precision <= 0) {
            throw new IllegalArgumentException("Precision must be positive");
        }

        // Validate both components without losing or rounding any digits.
        ComplexInputParser.ParsedComplex parsed = ComplexInputParser.parse(
                real + signedImaginary(imaginary) + "i"
        );

        Path worker = extractWorker();
        String python = findPython();
        Process process = new ProcessBuilder(
                python,
                worker.toString(),
                parsed.real(),
                parsed.imaginary(),
                Long.toString(precision)
        ).start();

        Map<String, String> fields = new HashMap<>();
        try (BufferedReader standardOutput = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = standardOutput.readLine()) != null) {
                int separator = line.indexOf('\t');
                if (separator > 0) {
                    fields.put(line.substring(0, separator), line.substring(separator + 1));
                }
            }
        }

        String errorOutput;
        try (BufferedReader standardError = process.errorReader(StandardCharsets.UTF_8)) {
            errorOutput = standardError.lines().reduce("", (left, right) -> left + right + "\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(
                    "The FLINT/Arb worker failed with exit code " + exitCode + ":\n" + errorOutput
            );
        }

        boolean certified = Boolean.parseBoolean(requireField(fields, "CERTIFIED"));
        if (!certified) {
            throw new IOException("The worker did not certify a unique interval rounding");
        }

        CertifiedResult result = new CertifiedResult(
                requireField(fields, "REAL"),
                requireField(fields, "IMAGINARY"),
                certified,
                Long.parseLong(requireField(fields, "PRECISION_DIGITS")),
                Long.parseLong(requireField(fields, "WORKING_DIGITS")),
                Long.parseLong(requireField(fields, "REAL_ACCURACY_BITS")),
                Long.parseLong(requireField(fields, "IMAGINARY_ACCURACY_BITS")),
                Double.parseDouble(requireField(fields, "TIME_SECONDS")),
                requireField(fields, "SHA256")
        );

        String localHash = sha256(result.real() + "\n" + result.imaginary() + "\n");
        if (!localHash.equals(result.sha256())) {
            throw new IOException("The worker result failed its SHA-256 integrity check");
        }

        return result;
    }

    private static String signedImaginary(String imaginary) {
        return imaginary.startsWith("-") || imaginary.startsWith("+")
                ? imaginary
                : "+" + imaginary;
    }

    private static long parsePrecision(String value) {
        try {
            long precision = Long.parseLong(value);
            if (precision <= 0) {
                throw new IllegalArgumentException("Precision must be positive");
            }
            return precision;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Precision must be a positive 64-bit integer", e);
        }
    }

    private static Path extractWorker() throws IOException {
        try (InputStream input = ComplexSuperfactorial.class.getResourceAsStream(WORKER_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled FLINT/Arb worker not found: " + WORKER_RESOURCE);
            }

            Path worker = Files.createTempFile("certified-superfactorial-", ".py");
            Files.copy(input, worker, StandardCopyOption.REPLACE_EXISTING);
            worker.toFile().deleteOnExit();
            return worker;
        }
    }

    private static String findPython() throws IOException, InterruptedException {
        String configured = System.getenv(PYTHON_ENVIRONMENT_VARIABLE);
        if (configured != null && !configured.isBlank() && canRunPython(configured)) {
            return configured;
        }

        for (String candidate : new String[] {"python3", "python"}) {
            if (canRunPython(candidate)) {
                return candidate;
            }
        }

        throw new IOException(
                "Python 3 was not found. Install Python or set the PYTHON environment variable."
        );
    }

    private static boolean canRunPython(String command) throws InterruptedException {
        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            try (InputStream ignored = process.getInputStream()) {
                ignored.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static String requireField(Map<String, String> fields, String name) throws IOException {
        String value = fields.get(name);
        if (value == null) {
            throw new IOException("The worker did not return the required field " + name);
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void writeResult(String input, CertifiedResult result, Writer writer)
            throws IOException {
        writer.write("S(");
        writer.write(input);
        writer.write(") =\n");
        writer.write(result.real());
        writer.write(result.imaginary().startsWith("-") ? "\n- " : "\n+ ");
        writer.write(result.imaginary().startsWith("-")
                ? result.imaginary().substring(1)
                : result.imaginary());
        writer.write(" i\n\n");
        writer.write("precision_digits=");
        writer.write(Long.toString(result.precisionDigits()));
        writer.write("\nreal_accuracy_bits=");
        writer.write(Long.toString(result.realAccuracyBits()));
        writer.write("\nimaginary_accuracy_bits=");
        writer.write(Long.toString(result.imaginaryAccuracyBits()));
        writer.write("\nverification=lower_and_upper_interval_endpoints_round_identically");
        writer.write("\nsha256(real\\nimaginary\\n)=");
        writer.write(result.sha256());
        writer.write("\ntime_seconds=");
        writer.write(Double.toString(result.timeSeconds()));
        writer.write('\n');
        writer.flush();
    }

    private static void printCertificate(CertifiedResult result) {
        System.out.println("Certified significant digits: " + result.precisionDigits());
        System.out.println("Unique interval rounding: " + result.certified());
        System.out.println("Real accuracy: " + result.realAccuracyBits() + " bits");
        System.out.println("Imaginary accuracy: " + result.imaginaryAccuracyBits() + " bits");
        System.out.println("SHA-256: " + result.sha256());
        System.out.printf("Calculation time: %.3f s%n", result.timeSeconds());
    }

    /**
     * Decimal result and its numerical certificate.
     */
    public record CertifiedResult(
            String real,
            String imaginary,
            boolean certified,
            long precisionDigits,
            long workingDigits,
            long realAccuracyBits,
            long imaginaryAccuracyBits,
            double timeSeconds,
            String sha256
    ) {
        /** Returns the real component as a {@link BigDecimal} when desired. */
        public BigDecimal realBigDecimal() {
            return new BigDecimal(real);
        }

        /** Returns the imaginary component as a {@link BigDecimal} when desired. */
        public BigDecimal imaginaryBigDecimal() {
            return new BigDecimal(imaginary);
        }
    }
}
