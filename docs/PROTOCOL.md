# Wire Protocol — v1

The Windows client and the Linux server talk over a single TCP connection
(default port `2704`) using **newline-delimited JSON**: every message is one
UTF-8 JSON object followed by `\n`. Messages never contain raw newlines
(JSON string escaping takes care of that).

## Envelope

Request (client → server):

```json
{"id": 7, "type": "metrics", "payload": {}}
```

| Field     | Type    | Notes                                                  |
|-----------|---------|--------------------------------------------------------|
| `id`      | integer | Chosen by the client, unique per connection.           |
| `type`    | string  | One of the request types below.                        |
| `payload` | object  | Request arguments. May be omitted or `{}` when unused. |

Response (server → client), always echoing the request `id`:

```json
{"id": 7, "ok": true, "data": {}}
{"id": 7, "ok": false, "error": {"code": "NOT_FOUND", "message": "No process with PID 4242"}}
```

The server processes requests **concurrently** and may answer them **out of
order** (a long `shell.exec` must not block `metrics`). The client correlates
responses by `id`. A request that cannot be parsed at all is answered with
`id: -1` and `BAD_REQUEST`.

### Error codes

| Code                | Meaning                                                     |
|---------------------|-------------------------------------------------------------|
| `BAD_REQUEST`       | Malformed JSON, unknown `type`, or invalid payload.         |
| `UNAUTHENTICATED`   | Any request other than `auth`/`ping`/`bye` before login.    |
| `AUTH_FAILED`       | Wrong username or password.                                 |
| `NOT_FOUND`         | Referenced resource (e.g. PID) does not exist.              |
| `PERMISSION_DENIED` | The OS refused the operation (e.g. killing a root process). |
| `INTERNAL`          | Unexpected server error.                                    |

After **3** failed `auth` attempts the server closes the connection.

## Request types

### `ping`
Payload: none. Data: `{"serverTime": 1732300000000}` (epoch millis).

### `bye`
Payload: none. Data: `{}`. The server closes the connection after answering.

### `auth`
Payload: `{"username": "admin", "password": "secret"}`

Data:
```json
{"username": "admin", "serverVersion": "1.0.0", "system": { /* same as system.info */ }}
```

### `system.info`
Data:
```json
{
  "hostname": "ubuntu-srv",
  "os": "Ubuntu 24.04.1 LTS",
  "kernel": "6.8.0-45-generic",
  "architecture": "amd64",
  "cpuModel": "Intel(R) Core(TM) i7-10750H CPU @ 2.60GHz",
  "cpuCores": 12,
  "memoryTotalBytes": 16624517120,
  "bootTime": 1732290000000
}
```

### `metrics`
Latest sample from the server's background sampler (refreshed every second).
Data:
```json
{
  "timestamp": 1732300000000,
  "uptimeSeconds": 10000,
  "loadAverage": [0.42, 0.37, 0.30],
  "processCount": 213,
  "cpu": {"usagePercent": 12.5, "perCore": [10.0, 15.0]},
  "memory": {
    "totalBytes": 16624517120, "usedBytes": 5120000000, "availableBytes": 11504517120,
    "swapTotalBytes": 2147479552, "swapUsedBytes": 0
  },
  "disks": [
    {"mount": "/", "device": "/dev/sda2", "fsType": "ext4", "totalBytes": 250000000000, "usedBytes": 90000000000}
  ],
  "network": {"rxBytesPerSec": 10240.0, "txBytesPerSec": 2048.0, "rxTotalBytes": 123456789, "txTotalBytes": 23456789}
}
```
`usedBytes` for memory is `total - available`. `disks` lists real block-device
and overlay filesystems only (no `tmpfs`, `proc`, `sysfs`, ...). `network`
excludes the loopback interface.

### `processes.list`
Data:
```json
{
  "processes": [
    {
      "pid": 1, "ppid": 0, "name": "systemd", "user": "root", "state": "S",
      "cpuPercent": 0.0, "memoryBytes": 13000000, "memoryPercent": 0.08,
      "threads": 1, "startTime": 1732290000000, "command": "/sbin/init splash"
    }
  ]
}
```
`cpuPercent` is relative to one core (like `top`), computed between sampler
ticks. `command` is the full command line, or `[name]` for kernel threads.

### `process.kill`
Payload: `{"pid": 4242, "signal": "TERM"}` — `signal` is `TERM` (default) or `KILL`.
Data: `{}`. Errors: `NOT_FOUND`, `PERMISSION_DENIED`, `BAD_REQUEST` (PID ≤ 1 or the
server's own PID).

### `shell.exec`
Payload: `{"command": "ls -la | grep java", "timeoutSeconds": 30}`
(`timeoutSeconds` optional, default 30, max 300).

Runs `command` with `bash -c` in the session's current working directory.
Pipes, redirections, quoting, globbing and `&&` work as in a terminal. A `cd`
(alone or chained) **persists** the working directory for the rest of the
session. The initial working directory is the authenticated server process
user's home.

Data:
```json
{
  "stdout": "...", "stderr": "...", "exitCode": 0,
  "cwd": "/home/admin", "durationMillis": 12, "timedOut": false, "truncated": false
}
```
stdout and stderr are each capped at 1 MiB (`truncated: true` when cut). On
timeout the process tree is killed, `timedOut` is `true` and `exitCode` is `-1`.
Interactive programs (`vim`, `top` without `-b`) are not supported; stdin is closed.
