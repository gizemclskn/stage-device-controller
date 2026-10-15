# Protocol

Newline-delimited JSON over TCP. One JSON object per line (`\n`), UTF-8. Default port: 5000.

## Rules

- Channels are numbered 1-8.
- `value` is an integer between 0 and 100. Decimals, strings, `true/false` and out-of-range numbers are rejected with `INVALID_VALUE`.
- Mute applies to audio channels only, so `set_mute` has no `type` field.
- Maximum line length is 1 KB. A longer line is rejected with `INVALID_JSON`, the rest of the line is discarded and the connection stays open.
- Initial state: all lights 0, all audio levels 0, mute off.
- In `get_state`, array index 0 is Channel 1.

## Commands

| Command | Request | Success response |
|---|---|---|
| ping | `{"cmd":"ping"}` | `{"status":"ok","cmd":"pong"}` |
| set_level | `{"cmd":"set_level","type":"light","channel":3,"value":75}` | same fields + `"status":"ok"` |
| set_mute | `{"cmd":"set_mute","channel":2,"mute":true}` | `{"status":"ok","cmd":"set_mute","channel":2,"mute":true}` |
| get_state | `{"cmd":"get_state"}` | `{"status":"ok","cmd":"get_state","light":[8 numbers],"audio":[{"level":..,"mute":..} x8]}` |

For `set_level`, `type` is `light` or `audio`.
The `"cmd":"pong"` in the ping response is a deliberate design choice.

## Errors

Error response format:

```json
{"status":"error","code":"INVALID_VALUE","message":"..."}
```

`message` is for humans. Clients should only look at `code`.

| Situation | Code |
|---|---|
| Malformed JSON, valid JSON that is not an object (`5`, `[1]`), empty line, line longer than 1 KB | `INVALID_JSON` |
| Unknown or missing `cmd` | `UNKNOWN_CMD` |
| `channel` missing, not an integer, or outside 1-8 | `INVALID_CHANNEL` |
| `type` missing or not `light`/`audio` | `INVALID_VALUE` |
| `value` missing, decimal, string, `true/false`, or outside 0-100 | `INVALID_VALUE` |
| `mute` is not a boolean | `INVALID_VALUE` |

## Timeouts

- Client connect timeout: 3 s. Client read timeout: 3 s.
- On a read timeout the client treats the request as failed and marks the connection as lost, so a late response can never be mistaken for the answer to the next command.
- The server closes a client that sends nothing for 300 s (`--idle-timeout`).

## Fault injection (server options)

- `--latency-ms N`: delays every response by N milliseconds.
- `--drop-rate X`: does not answer a fraction X (0-1) of commands.
