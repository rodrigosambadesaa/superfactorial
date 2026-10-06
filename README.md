# Parallel Superfactorial

Proyecto Java 17/Eclipse para calcular:

```text
S(n) = 1! × 2! × ... × n!
```

Incluye dos implementaciones:

- entero exacto de alto rendimiento, con FLINT/GMP y respaldo Java puro;
- continuación compleja con precisión arbitraria certificada mediante FLINT/Arb.

## Enteros exactos: FLINT/GMP

Para entradas grandes se calcula primero el exponente de cada primo:

```text
v_p(S(n)) = Σ(j≥1) Σ(k=1..n) floor(k / p^j)
```

Las potencias primas se multiplican en un árbol que combina primero operandos de
tamaño parecido. Las operaciones gigantes se ejecutan en FLINT/GMP. Si el backend
nativo no está instalado, se conserva el algoritmo Java exacto con criba de primos,
árbol ponderado y `ForkJoinPool`.

API:

```java
BigInteger value = ParallelSuperfactorial.superfactorial(n);
```

Para resultados gigantes conviene escribir directamente a un archivo. Así no se
paga la conversión decimal mucho más lenta de `BigInteger.toString()`:

```bash
java -cp target/classes programas.ParallelSuperfactorial 10000 superfactorial-10000.txt
```

El archivo se genera primero con un nombre temporal, se comprueban su longitud y
SHA-256 y finalmente se mueve de forma atómica a su destino.

## Complejos certificados

La continuación analítica usada es:

```text
S(z) = G(z + 2)
```

`G` es la función G de Barnes. El argumento decimal se conserva como texto y se
convierte a un racional exacto; nunca pasa por `double`. Arb propaga intervalos
complejos rigurosos y solo se devuelve un resultado cuando todo el intervalo
redondea a los mismos dígitos pedidos.

```java
ComplexSuperfactorial.CertifiedResult result =
        ComplexSuperfactorial.superfactorial("1.1i", 100_000L);
```

```bash
java -cp target/classes programas.ComplexSuperfactorial \
  1.1i 100000 superfactorial-1.1i-100000.txt
```

No hay un límite artificial pequeño de precisión. La precisión se recibe como
`long` y los componentes se devuelven como `String`; los límites reales son
tiempo, memoria y los tamaños máximos de las estructuras de Java/Python/FLINT.

## Instalación y Eclipse

Requisitos: Java 17, Maven, Python 3 y Eclipse con m2e.

```bash
python3 -m pip install -r requirements.txt
mvn verify
```

En Eclipse: **File → Import → Maven → Existing Maven Projects** y seleccionar este
repositorio. Si el Python deseado no se llama `python3` o `python`, definir la
variable de entorno `PYTHON`.

`requirements.txt` fija `python-flint==0.8.0`. El número de hilos nativos se
puede ajustar con `SUPERFACTORIAL_THREADS`.

## Verificación

Las transferencias binarias y los archivos decimales llevan comprobación SHA-256
y de longitud/bit-length. El proyecto incluye pruebas cruzadas contra la versión
Java y una acción de GitHub que instala FLINT y ejecuta `mvn verify`.

La validación compleja de 100.000 dígitos existente se conserva en `validation/`.
