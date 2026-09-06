# File status HTTP API

The status API exposes the current in-memory file security state to external systems without allowing them to mutate state.

Enable it in `config.yaml`:

```yaml
statusApi:
  enabled: true
  host: "127.0.0.1"
  port: 8080
```

All file paths are relative to the configured source root and use source-tree semantics. Attempts to escape the source root are rejected.

## Get one file

```http
GET /api/v1/files/state?path=uploads/example.txt
```

Tracked response:

```json
{
  "path": "uploads/example.txt",
  "state": "SAFE",
  "tracked": true,
  "safe": true
}
```

A path with no active registry entry is still a successful query:

```json
{
  "path": "uploads/missing.txt",
  "state": "UNTRACKED",
  "tracked": false,
  "safe": false
}
```

`UNTRACKED` is an API-level result, not a `FileState` value.

Current runtime states are:

- `UNSCANNED`
- `SCANNING`
- `SAFE`
- `FLAGGED`
- `QUARANTINED`
- `ERROR`

A deleted file has no active state and therefore queries as `UNTRACKED`.

## List tracked files

```http
GET /api/v1/files/states
```

```json
{
  "count": 2,
  "files": [
    {
      "path": "a.txt",
      "state": "SAFE",
      "tracked": true,
      "safe": true
    },
    {
      "path": "uploads/b.txt",
      "state": "FLAGGED",
      "tracked": true,
      "safe": false
    }
  ]
}
```

Results are sorted by relative path.

Filter by a source-relative path prefix:

```http
GET /api/v1/files/states?prefix=uploads
```

Prefix matching is path-segment based, so `uploads` does not match `uploads-other`.

## Health

```http
GET /api/v1/health
```

```json
{
  "status": "UP"
}
```

## Errors

Invalid requests use JSON error responses, for example:

```json
{
  "error": "INVALID_REQUEST",
  "message": "Path escapes the configured source root: ../outside.txt"
}
```

The API currently supports only `GET`. Responses use `Cache-Control: no-store` because file state can change at any moment.

## Network security

There is currently no authentication or authorization layer. The default bind host is therefore `127.0.0.1`. Bind to `0.0.0.0` only when network access to the port is controlled externally, for example by Docker networking, a reverse proxy, firewall rules, or a private service network.
