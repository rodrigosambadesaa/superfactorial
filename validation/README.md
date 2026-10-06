# 100,000-digit validation

The file [`superfactorial-1.1i-100000.txt`](superfactorial-1.1i-100000.txt) contains the certified calculation:

```text
S(1.1i) = G(2 + 1.1i)
```

The decimal input `1.1` was converted exactly to the rational number `11/10`; it was not converted through binary floating point.

## Certificate

| Property | Value |
| --- | --- |
| Requested significant digits | 100,000 |
| Internal decimal precision | 100,064 |
| Real relative accuracy | 332,386 bits |
| Imaginary relative accuracy | 332,385 bits |
| FLINT/Arb calculation time | 1,422.486822223 seconds |
| Canonical result SHA-256 | `bfa718e1f16999ac9abc23516ffbcf7086f83c09438fb9abae1c4eead31685eb` |
| Complete result-file SHA-256 | `d801aeeaf7824af5f1b630d932e0f5d2396d7808f1b8b5d3fa002d8e96941c46` |

The canonical digest covers exactly:

```text
real-component + newline + imaginary-component + newline
```

The worker evaluated the function with Arb complex ball arithmetic. It accepted each component only after the lower and upper endpoints of the resulting interval rounded to the same 100,000-significant-digit decimal string.

An independent calculation with `mpmath 1.3.0` at 220 decimal digits was rounded to 200 digits. Both the real and imaginary components matched the corresponding 200-digit rounding of the Arb result.

## Visible endpoints

The real component begins and ends with:

```text
0.8646246885252490613265372839692806209846347220731636338786354843558985510509721594785262327664837461
...
5615932061090486155555088154305037520272242598781255600995589343471074029797087740754867858716244720
```

The imaginary component begins and ends with:

```text
-0.3235120698516650539511816804111035648858394244142932089811605367437225352291072174798019854585879311
...
9408969444234566780880941623464046008327559666990995896894937049408760772440134859629961803363461800
```
