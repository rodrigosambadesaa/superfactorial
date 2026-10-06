#!/usr/bin/env python3
"""Fast exact integer superfactorial worker using FLINT/GMP."""

from __future__ import annotations

import hashlib
import heapq
import os
import sys
import time
from pathlib import Path

try:
    from flint import ctx, fmpz
except ImportError as error:
    raise SystemExit(
        "python-flint is required. Install it with: "
        "python3 -m pip install -r requirements.txt"
    ) from error


def primes_up_to(limit: int):
    """Yield primes through limit from a compact odd-only sieve."""
    if limit >= 2:
        yield 2
    if limit < 3:
        return

    size = (limit + 1) // 2
    sieve = bytearray(b"\x01") * size
    sieve[0] = 0
    root = int(limit ** 0.5)
    for prime in range(3, root + 1, 2):
        if sieve[prime // 2]:
            start = prime * prime // 2
            count = ((size - 1 - start) // prime) + 1
            sieve[start::prime] = b"\x00" * count
    for index in range(1, size):
        if sieve[index]:
            prime = 2 * index + 1
            if prime <= limit:
                yield prime


def prime_exponent(number: int, prime: int) -> int:
    """Return v_p(product(k!, k=1..n))."""
    exponent = 0
    power = prime
    while power <= number:
        quotient, remainder = divmod(number, power)
        exponent += power * quotient * (quotient - 1) // 2
        exponent += quotient * (remainder + 1)
        if power > number // prime:
            break
        power *= prime
    return exponent


def minimum_product(values) -> fmpz:
    """Multiply similarly sized operands first (optimal merge product tree)."""
    heap = []
    sequence = 0
    for value in values:
        heap.append((value.bit_length(), sequence, value))
        sequence += 1
    if not heap:
        return fmpz(1)
    heapq.heapify(heap)
    while len(heap) > 1:
        _, _, left = heapq.heappop(heap)
        _, _, right = heapq.heappop(heap)
        product = left * right
        heapq.heappush(heap, (product.bit_length(), sequence, product))
        sequence += 1
    return heap[0][2]


def superfactorial(number: int) -> fmpz:
    factors = (
        fmpz(prime) ** prime_exponent(number, prime)
        for prime in primes_up_to(number)
    )
    return minimum_product(factors)


def main() -> None:
    if len(sys.argv) != 4 or sys.argv[1] not in {"binary", "decimal"}:
        raise SystemExit("usage: worker (binary|decimal) N OUTPUT")

    mode, number_text, output_text = sys.argv[1:]
    number = int(number_text)
    if number < 0:
        raise ValueError("n must be non-negative")

    ctx.threads = max(1, int(os.environ.get(
        "SUPERFACTORIAL_THREADS", min(8, os.cpu_count() or 1))))

    calculation_start = time.perf_counter()
    value = superfactorial(number)
    calculation_seconds = time.perf_counter() - calculation_start

    conversion_start = time.perf_counter()
    bit_length = value.bit_length()
    if mode == "binary":
        payload = int(value).to_bytes((bit_length + 7) // 8, "big")
        digits = 1 if number < 2 else 0
    else:
        text = str(value)
        payload = text.encode("ascii")
        digits = len(text)
    conversion_seconds = time.perf_counter() - conversion_start

    write_start = time.perf_counter()
    Path(output_text).write_bytes(payload)
    write_seconds = time.perf_counter() - write_start

    fields = {
        "DIGITS": digits,
        "BIT_LENGTH": bit_length,
        "CALCULATION_SECONDS": f"{calculation_seconds:.9f}",
        "CONVERSION_SECONDS": f"{conversion_seconds:.9f}",
        "WRITE_SECONDS": f"{write_seconds:.9f}",
        "SHA256": hashlib.sha256(payload).hexdigest(),
    }
    for name, field_value in fields.items():
        print(f"{name}\t{field_value}")


if __name__ == "__main__":
    main()
