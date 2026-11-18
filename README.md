# UART Protocol Tester

A reusable, template-driven Java framework for UART communication protocol simulation and testing. The tool loads plain-text `.properties` templates, feeds test bytes through a fixed-size `FrameBuffer`, transmits each array over UART (or in-memory loopback), verifies the reply against configured rules, logs every event with timestamps, and records test results to SQLite before processing subsequent frames.

## Build and Test

Requirements: Java 17+ and Maven.

```shell
mvn -Dmaven.repo.local=.m2/repository clean test
mvn -Dmaven.repo.local=.m2/repository clean package
```

The build produces a self-contained executable shaded JAR at `target/uart-protocol-tester-1.0-SNAPSHOT.jar`.

## Running Tests

### 1. In-Memory Loopback Simulation (Default / Driver-Free)

Runs both the tester and the simulated device in-memory without OS drivers:

```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties loopback
```

In loopback mode, `Main` creates a thread-safe `LoopbackUartPort` pair, launches `DeviceSimulator` in the background with the template's `device` rule, transmits all array frames, verifies replies, and cleanly shuts down threads and resources.

### 2. Real or Virtual Serial Port Mode

Connects to hardware transceivers or OS serial ports via `jSerialComm`:

```shell
# Terminal 1: Optional fake device
java -cp target/uart-protocol-tester-1.0-SNAPSHOT.jar com.bel.uarttester.DeviceSimulator templates/nibble-swap.properties COM6

# Terminal 2: Test runner
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar templates/nibble-swap.properties
```

### 3. Viewing Stored Results

Displays the last 20 test results from SQLite in an aligned table:

```shell
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar results
# Or specify custom database path:
java -jar target/uart-protocol-tester-1.0-SNAPSHOT.jar results data/results.db
```

## Exit Codes

- `0`: All array frames completed and passed verification (or `results` command succeeded).
- `1`: Test completed, but at least one array frame failed verification or timed out.
- `2`: CLI syntax error, invalid template configuration, port open failure, or fatal runtime error.

## Template Key Reference

Templates are `.properties` files (read as UTF-8) configuring protocol parameters:

| Key | Required | Default | Description |
|:---|:---|:---|:---|
| `name` | No | File basename | Test scenario identifier used in logs and database records |
| `port` | Yes | - | System serial port name (e.g., `COM5`, `/dev/ttyUSB0`) |
| `baud` | Yes | - | Positive integer baud rate (e.g., `9600`, `115200`) |
| `dataBits` | Yes | - | Number of data bits: `5`, `6`, `7`, or `8` |
| `parity` | Yes | - | Parity mode: `none`, `even`, or `odd` (case-insensitive) |
| `stopBits` | Yes | - | Number of stop bits: `1` or `2` |
| `arraySize`| No | `8` | Fixed array size in bytes (`> 0`) |
| `send` | Yes | - | Space-separated hex bytes to transmit (`00`–`FF`) |
| `expect` | Yes | - | Space-separated hex bytes expected in reply (`00`–`FF`) |
| `verify` | Yes | - | Verifier rule: `NIBBLE_SWAP`, `EXACT_MATCH`, or custom FQCN |
| `device` | No | `NIBBLE_SWAP`| Simulation behavior: `NIBBLE_SWAP`, `ECHO`, `ADD:<hex>`, `XOR:<hex>`, `FIXED:<hex bytes>` |
| `timeoutMs`| Yes | - | Response deadline per array frame in milliseconds (`> 0`) |

> **Frame Length Rule:** Both `send` and `expect` must contain equal counts of bytes that are exact positive multiples of `arraySize` (e.g., `arraySize=4` with 12 bytes processes 3 sequential arrays).

## Adding Custom Verifiers

1. Create a class implementing `com.bel.uarttester.Verifier` with a public no-argument constructor:
   ```java
   package com.example;
   import com.bel.uarttester.Verifier;

   public class CustomChecksumVerifier implements Verifier {
       @Override
       public boolean verify(byte[] sent, byte[] received, byte[] expected) {
           return java.util.Arrays.equals(received, expected);
       }
   }
   ```
2. Reference the fully-qualified class name directly in the template:
   ```properties
   verify=com.example.CustomChecksumVerifier
   ```
   `TestRunner` instantiates the class dynamically via reflection without modifying core engine code.

## Standard Log Format

Logs are mirrored to the console and to `logs/run.log` (tester) and `logs/device.log` (device):

```text
RUN START <template-name>
timestamp | SOURCE | EVENT | hex bytes | detail
```

- **`SOURCE`**: `TESTER` or `DEVICE`
- **`EVENT`**: `INFO`, `SEND`, `RECEIVE`, `RESULT`, `ERROR`
- Aligned columns; trailing detail column is omitted when empty.

## Storage Schema

Results are persisted in SQLite at `data/results.db` in table `results`:
- `id` (INTEGER PRIMARY KEY)
- `created_at` (TEXT timestamp)
- `template` (TEXT scenario name)
- `array_no` (INTEGER 1-based array index)
- `sent` (TEXT space-separated hex bytes)
- `received` (TEXT space-separated hex bytes)
- `expected` (TEXT space-separated hex bytes)
- `verdict` (TEXT `PASS` or `FAIL`)

## In-Memory Simulation vs Hardware UART

`LoopbackUartPort` provides an application-level, thread-safe byte queue simulation. It accurately verifies frame chunking, byte values, ordering, verifier algorithms, timeouts, and storage. However, it does not physically model electrical signaling, clock skew/jitter, line noise, framing errors, or hardware parity bits. For physical line validation, run against physical serial hardware via `SerialUartPort`.
