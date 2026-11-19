# UART Protocol Tester

A reusable, template-driven Java framework for UART communication protocol simulation, testing, and verification. The tool loads plain-text `.properties` templates, chunks byte streams through a fixed-size `FrameBuffer`, frames and transmits each array over UART (in-memory loopback or real hardware serial port), verifies replies against configurable rules and wildcards, logs structured trace events with daily rotation, and persists execution metrics and failure diagnostics to SQLite before processing subsequent frames.

---

## Architecture Overview

```
                      +-------------------+
                      |   Template (.prop)|
                      +---------+---------+
                                |
             +------------------+------------------+
             |                                     |
   +---------v---------+                 +---------v---------+
   |    FrameBuffer    |                 |    FrameCodec     |
   | (multi-array split|                 | (framing/checksum)|
   +---------+---------+                 +---------+---------+
             |                                     |
             +------------------+------------------+
                                |
                      +---------v---------+
                      |    TestRunner     |
                      +----+---------+----+
                           |         |
          +----------------+         +----------------+
          |                                           |
+---------v---------+                       +---------v---------+
|     UartPort      |                       |     Verifier      |
| (Loopback/Serial) |                       | (Exact/Nibble/FQCN|
+---------+---------+                       +-------------------+
          |
+---------v---------+
|  DeviceSimulator  |
|  (Rule + Faults)  |
+---------+---------+
          |
+---------v---------+         +-------------------+
|      TestLog      |         |    ResultStore    |
| (console + files) |         | (SQLite + Latency)|
+-------------------+         +---------+---------+
                                        |
                              +---------v---------+
                              |    CsvExporter    |
                              | (RFC 4180 export) |
                              +-------------------+
```

- **`Template`**: Loads and validates protocol parameters, framing rules, fault injection settings, and byte sequences.
- **`FrameBuffer`**: Splits arbitrary-length payloads into fixed `arraySize` blocks for sequential transmission.
- **`FrameCodec`**: Encodes raw payloads into framed packets (`frameStart`, `frameLength`, `checksum`, `frameEnd`) and decodes responses.
- **`UartPort`**: Transport interface implemented by `SerialUartPort` (jSerialComm) and `LoopbackUartPort` (in-memory queues with optional baud timing).
- **`DeviceSimulator` & `DeviceRule`**: Simulated UART peer executing behavioral rules (`NIBBLE_SWAP`, `ECHO`, `ADD:<hex>`, `XOR:<hex>`, `FIXED:<hex>`) and configurable fault injections (`faultDelayMs`, `faultDrop`, `faultCorrupt`, `faultPartialBytes`).
- **`Verifier`**: Pluggable verification engine supporting `EXACT_MATCH` (with `??` wildcards), `NIBBLE_SWAP`, and custom verifiers via reflection.
- **`TestLog`**: Standardized event logger writing aligned lines to console and daily rotated log files with automatic hex truncation.
- **`ResultStore` & `CsvExporter`**: Persists run batches to SQLite (`data/results.db`) with run IDs, latency measurements, failure diagnostics, WAL mode, and RFC 4180 CSV export.

---

## Build and Package

Requirements: Java 17+ and Maven.

```shell
# Run test suite
mvn clean test

# Build executable shaded JAR
mvn clean package
```

The build produces a self-contained executable shaded JAR at `target/uart-protocol-tester-1.0-SNAPSHOT.jar`.

---

## CLI Modes & Usage

### 1. Single Template Execution
```shell
# In-memory loopback simulation (default driver-free mode)
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties loopback

# Hardware / virtual serial port mode
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties
```

### 2. Batch Execution
Executes all `*.properties` templates in a directory sequentially and displays a summary table:
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/ loopback
```

### 3. Port Discovery
Lists all detected system serial ports and descriptive names (exits with code 0):
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar ports
```

### 4. Viewing Stored Results
Displays recent test runs from the SQLite database with run ID, timestamps, hex exchanges, latency, and verdicts:
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar results
# Or specify database file path:
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar results path/to/results.db
```

### 5. CSV Export
Exports SQLite records to RFC 4180-compliant CSV:
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar export target/report.csv
# Or specify database file path:
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar export target/report.csv path/to/results.db
```

---

## Redirecting Output Directory (`-Duarttester.out`)

You can redirect where `logs/` and `data/` are written using the `uarttester.out` system property:

```shell
java -Duarttester.out=/path/to/output -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties loopback
```
When specified:
- Run logs are written to `/path/to/output/logs/run-YYYY-MM-DD.log`
- Device logs are written to `/path/to/output/logs/device-YYYY-MM-DD.log`
- SQLite results are stored in `/path/to/output/data/results.db`
- `results` and `export` CLI commands also default to `/path/to/output/data/results.db`

---

## Exit Codes

- `0`: All array frames completed and passed verification (or `ports`, `results`, `export` succeeded).
- `1`: Test completed, but at least one array frame failed verification or timed out.
- `2`: CLI syntax error, invalid template configuration, empty template directory, port open failure, or fatal runtime error.

---

## Template Key Reference

Templates are standard UTF-8 `.properties` files:

| Key | Required | Default | Description |
|:---|:---|:---|:---|
| `name` | No | File basename | Identifier for the test scenario |
| `port` | Yes | - | Serial port name (e.g., `COM5`, `/dev/ttyUSB0`) |
| `baud` | Yes | - | Baud rate (positive integer, e.g., `9600`, `115200`) |
| `dataBits` | Yes | - | Data bits: `5`, `6`, `7`, or `8` |
| `parity` | Yes | - | Parity mode: `none`, `even`, or `odd` (case-insensitive) |
| `stopBits` | Yes | - | Stop bits: `1` or `2` |
| `arraySize`| No | `8` | Payload chunk size in bytes (`> 0`; max 255 if `frameLength=true`) |
| `send` | Yes | - | Space-separated hex bytes to transmit (`00`–`FF`) |
| `expect` | Yes | - | Space-separated hex bytes (`00`–`FF`) or `??` wildcards expected |
| `verify` | Yes | - | Verifier: `NIBBLE_SWAP`, `EXACT_MATCH`, or custom class FQCN |
| `device` | No | `NIBBLE_SWAP`| Simulator rule: `NIBBLE_SWAP`, `ECHO`, `ADD:<hex>`, `XOR:<hex>`, `FIXED:<hex>`, `SILENT` |
| `timeoutMs`| Yes | - | Response timeout per array frame in milliseconds (`> 0`) |
| `frameStart`| No | None | Optional frame start hex byte (e.g., `AA`) |
| `frameLength`| No | `false` | When `true`, includes 1-byte payload length in frame wire header |
| `checksum` | No | `NONE` | Checksum algorithm: `NONE`, `XOR`, `SUM` (modulo 256), or `CRC8` (polynomial 0x07) |
| `frameEnd` | No | None | Optional frame end hex byte (e.g., `55`) |
| `faultDelayMs`| No | `0` | Simulator response delay in milliseconds |
| `faultDrop`| No | `false` | When `true`, simulator drops reply entirely |
| `faultCorrupt`| No | `NONE` | Injects bit flip: `NONE` or `FLIP_BIT:<byteIndex>` (e.g., `FLIP_BIT:1`) |
| `faultPartialBytes`| No | `0` | If `> 0`, simulator transmits only the first `N` bytes of reply |
| `baudTiming`| No | `false` | When `true`, loopback simulates physical UART bit transmission delays |

---

## Log Format & Daily Rotation

Logs are written simultaneously to console and daily rotated log files:
- `logs/run-YYYY-MM-DD.log`: Master test runner execution log
- `logs/device-YYYY-MM-DD.log`: Device simulator event log

### Line Format
```
YYYY-MM-DD HH:mm:ss.SSS | SOURCE | EVENT   | hex bytes | detail
```
- `SOURCE`: `TESTER` or `DEVICE`
- `EVENT`: `SEND`, `RECEIVE`, `RESULT`, `INFO`, `WARN`, `ERROR`
- `hex bytes`: Byte array formatted in uppercase hex. **For frames larger than 32 bytes, the byte array is truncated before formatting to the first 32 bytes followed by ` ... (N bytes)`** (e.g., `00 01 02 ... 1F ... (100000 bytes)`). Frames $\le 32$ bytes are output in full without truncation.
- `detail`: Array index, latency measurement, or diagnostic messages.

---

## Failure Diagnostic Messages

When a test frame fails, the runner produces specific diagnostic messages across logs, console output, and the SQLite `detail` column:

1. **Timeout (no reply received within deadline):**
   ```
   FAIL - timeout: no reply within 500 ms (501.20 ms)
   ```
2. **Short Reply (fewer bytes returned than expected wire size):**
   ```
   FAIL - short reply: 2 of 4 bytes (110.15 ms)
   ```
3. **Bad Frame (framing, delimiter, or checksum error):**
   ```
   FAIL - bad frame: Checksum mismatch (2.10 ms)
   ```
4. **Content Mismatch (payload verification difference):**
   ```
   FAIL - 1 of 4 bytes differ; first at byte 1: expected B1, got B0 (0.45 ms)
   ```
   - Skips wildcard (`??`) positions.
   - Lists up to the first 5 differing 0-based byte positions.

---

## Adding a Custom Verifier

To implement a proprietary protocol validation algorithm, create a class implementing `com.bel.uarttester.Verifier`:

```java
package com.bel.custom;

import com.bel.uarttester.Verifier;

public class HeaderChecksumVerifier implements Verifier {
    @Override
    public boolean verify(byte[] sent, byte[] received, byte[] expected) {
        if (received.length < 2) return false;
        // Example custom check: ensure first byte matches expected and second is 0x00
        return received[0] == expected[0] && received[1] == 0x00;
    }
}
```

In your `.properties` template, configure `verify` with the Fully Qualified Class Name (FQCN):
```properties
verify=com.bel.custom.HeaderChecksumVerifier
```
`TestRunner` dynamically instantiates the custom verifier via reflection at runtime.

---

## Direct JUnit 5 Test Invocation

Developers can integrate UART test templates directly into JUnit 5 test suites by calling `Main.run`:

```java
package com.bel.custom;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.bel.uarttester.Main;
import com.bel.uarttester.Template;

class MyHardwareIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void runsTemplateInLoopback() throws Exception {
        Template t = Template.load("templates/nibble-swap.properties");
        // Main.run(template, loopback, baseDir)
        boolean passed = Main.run(t, true, tempDir.toString());
        assertTrue(passed, "Template execution should pass");
    }
}
```

---

## SQLite Database Schema

The SQLite database (`data/results.db`) is configured with WAL mode (`PRAGMA journal_mode=WAL;`), automatic schema migration, and transaction batching (commits every 1000 rows and on `close()`):

| Column | Type | Description |
|:---|:---|:---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | Unique sequence ID |
| `run_id` | `TEXT` | Unique execution identifier (e.g., `20261119_124930_e754`) |
| `created_at` | `TEXT NOT NULL` | ISO-8601 timestamp |
| `template` | `TEXT NOT NULL` | Name of the template |
| `array_no` | `INTEGER NOT NULL` | 1-based index of the array in multi-array test |
| `sent` | `TEXT NOT NULL` | Transmitted hex bytes (capped at 4096 bytes with ` ... (N bytes)`) |
| `received` | `TEXT NOT NULL` | Received hex bytes (capped at 4096 bytes with ` ... (N bytes)`) |
| `expected` | `TEXT NOT NULL` | Expected hex bytes (capped at 4096 bytes with ` ... (N bytes)`) |
| `latency_ms` | `REAL DEFAULT 0` | Round-trip latency in milliseconds |
| `verdict` | `TEXT NOT NULL` | `PASS` or `FAIL` |
| `detail` | `TEXT DEFAULT ''` | Failure diagnostic string on failure; empty on pass |

Indexes:
- `idx_results_tpl_time` on `(template, created_at)`
- `idx_results_run_id` on `(run_id)`

---

## Honest Limitations

- **Loopback Simulation vs. Hardware UART**: `LoopbackUartPort` provides thread-safe, in-memory queue transport with optional baud-rate bit delay timing. However, it operates in-process and does not model physical electrical signal characteristics such as bus noise, line reflections, voltage drop, start/stop bit skew, or hardware buffer overruns.
- **Real Serial Port Testing**: Hardware serial port support is fully implemented using `SerialUartPort` via `jSerialComm`. However, in this development and CI container environment, real physical serial hardware and virtual serial port drivers are unavailable, so serial port communication is validated via unit mocks and loopback simulation.
