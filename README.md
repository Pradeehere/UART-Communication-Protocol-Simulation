# UART Protocol Tester

A reusable, template-driven Java framework for UART communication protocol simulation and testing. The tool loads plain-text `.properties` templates, feeds test bytes through a fixed-size `FrameBuffer`, transmits each array over UART (or in-memory loopback), verifies the reply against configured rules, logs every event with timestamps, and records test results to SQLite before processing subsequent frames.

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

- **`Template`**: Loads, validates, and stores protocol parameters, framing rules, fault settings, and byte streams.
- **`FrameBuffer`**: Chunks arbitrary-length payloads into fixed `arraySize` blocks for sequential transmission.
- **`FrameCodec`**: Modulates raw payloads into framed wire packets (`frameStart`, `frameLength`, `checksum`, `frameEnd`) and decodes responses.
- **`UartPort`**: Hardware abstraction layer implemented by `SerialUartPort` (jSerialComm) and `LoopbackUartPort` (in-memory queues with optional baud timing).
- **`DeviceSimulator`**: Fake UART peer executing behavioral rules (`NIBBLE_SWAP`, `ECHO`, `ADD:<hex>`, `XOR:<hex>`, `FIXED:<hex>`) and configurable fault injections.
- **`Verifier`**: Pluggable verification engine supporting `EXACT_MATCH` (with `??` wildcards), `NIBBLE_SWAP`, and custom reflection-loaded verifiers.
- **`TestLog`**: Thread-safe structured event logger outputting timestamped trace lines to console and log files.
- **`ResultStore` & `CsvExporter`**: Persists array exchanges with microsecond round-trip latency to SQLite with automated schema migrations and RFC 4180 CSV export.

---

## Build and Test

Requirements: Java 17+ and Maven.

```shell
mvn clean test
mvn clean package
```

The build produces a self-contained executable shaded JAR at `target/uart-protocol-tester-1.0-SNAPSHOT.jar`.

---

## CLI Commands

### 1. Single Template Execution
```shell
# In-memory loopback simulation (driver-free)
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties loopback

# Real/virtual hardware serial port
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties
```

### 2. Batch Directory Execution
Executes all `*.properties` files in a folder sequentially, recording all events and results, and outputs a summary table:
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/ loopback
```

### 3. Serial Port Discovery
Lists all detected serial ports and descriptions (exits 0):
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar ports
```

### 4. Viewing Stored Results
Displays recent test runs with round-trip latency and dynamic min/avg/max statistics:
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar results
# Or specify database path:
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar results data/results.db
```

### 5. CSV Export
Exports SQLite records to RFC 4180-compliant CSV with header and proper escaping:
```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar export target/report.csv
# Or specify database path:
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar export target/report.csv data/results.db
```

---

## Exit Codes

- `0`: All array frames completed and passed verification (or `ports`, `results`, `export` succeeded).
- `1`: Test completed, but at least one array frame failed verification or timed out.
- `2`: CLI syntax error, invalid template configuration, empty template folder, port open failure, or fatal runtime error.

---

## Template Key Reference

Templates are standard UTF-8 `.properties` files:

| Key | Required | Default | Description |
|:---|:---|:---|:---|
| `name` | No | File basename | Test scenario identifier |
| `port` | Yes | - | System serial port name (e.g., `COM5`, `/dev/ttyUSB0`) |
| `baud` | Yes | - | Baud rate (positive integer, e.g., `9600`, `115200`) |
| `dataBits` | Yes | - | Number of data bits: `5`, `6`, `7`, or `8` |
| `parity` | Yes | - | Parity mode: `none`, `even`, or `odd` (case-insensitive) |
| `stopBits` | Yes | - | Number of stop bits: `1` or `2` |
| `arraySize`| No | `8` | Payload chunk size in bytes (`> 0`) |
| `send` | Yes | - | Space-separated hex bytes to transmit (`00`–`FF`) |
| `expect` | Yes | - | Space-separated hex bytes or `??` wildcards expected in reply |
| `verify` | Yes | - | Verifier rule: `NIBBLE_SWAP`, `EXACT_MATCH`, or custom FQCN |
| `device` | No | `NIBBLE_SWAP`| Simulation behavior: `NIBBLE_SWAP`, `ECHO`, `ADD:<hex>`, `XOR:<hex>`, `FIXED:<hex bytes>`, `SILENT` |
| `timeoutMs`| Yes | - | Response deadline per array frame in milliseconds (`> 0`) |
| `frameStart`| No | None | Optional frame start hex byte (e.g., `AA`) |
| `frameLength`| No | `false` | When `true`, includes 1-byte payload length in frame |
| `checksum` | No | `NONE` | Frame checksum: `NONE`, `XOR`, `SUM` (modulo 256), or `CRC8` (polynomial 0x07) |
| `frameEnd` | No | None | Optional frame end hex byte (e.g., `55`) |
| `faultDelayMs`| No | `0` | Simulator response delay in milliseconds |
| `faultDrop`| No | `false` | When `true`, simulator receives and logs frame but drops reply |
| `faultCorrupt`| No | `NONE` | Injects bit flip: `NONE` or `FLIP_BIT:<byteIndex>` (e.g., `FLIP_BIT:1`) |
| `faultPartialBytes`| No | `0` | If `> 0`, simulator transmits only the first `N` bytes of reply |
| `baudTiming`| No | `false` | When `true`, loopback simulates physical UART per-byte bit transmission delays |

---

## SQLite Storage Schema

Results are persisted in SQLite (`data/results.db`) in table `results`:
- `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
- `created_at` (TEXT ISO-8601 timestamp)
- `template` (TEXT scenario name)
- `array_no` (INTEGER 1-based array sequence index)
- `sent` (TEXT space-separated hex bytes transmitted)
- `received` (TEXT space-separated hex bytes received)
- `expected` (TEXT space-separated hex bytes or wildcards expected)
- `latency_ms` (REAL round-trip time in milliseconds)
- `verdict` (TEXT `PASS` or `FAIL`)
