# Core configuration syntax

Current syntax version: `1`.

The configured filesystem root is deliberately **not** stored in this YAML. It is supplied by the process/container. Every `RuleSet.path` is relative to that root.

## Top-level structure

```yaml
version: 1

ruleSets:
  - path: "."
    maxDepth: 3
    override: false
    rules: []
```

Fields:

- `version`: optional; defaults to `1`. Any unsupported version is rejected.
- `ruleSets`: optional list; defaults to empty.
- unknown fields are rejected.

## RuleSet

```yaml
- path: "private/uploads"
  maxDepth: 2
  override: true
  rules:
    - ...
```

- `path`: optional; blank/missing means `.`. Must be relative and may not escape the supplied source root.
- `maxDepth`: optional non-negative integer; omitted means unlimited.
- `override`: optional boolean; omitted means `false`.
- `rules`: optional ordered list. Rule order is preserved.

A rule entry must contain exactly one of `custom` or `predefined`.

---

# Actions

Current action names:

```yaml
flag
quarantine
delete
```

Action lists preserve configuration order, but runtime execution still obeys the global action phase ordering defined by the core (`FLAG -> QUARANTINE -> DELETE`, with later phases added separately).

`delete` requires a configured `LogHandler` at load/compile time.

`quarantine` requires both a configured `LogHandler` and `QuarantineService` at load/compile time.

`flag` has no external dependency.

An action branch may be omitted or empty.

```yaml
onMatch: [flag, quarantine]
onNoMatch: []
```

A single action scalar is also accepted:

```yaml
onMatch: flag
```

---

# Custom rules

Custom rules expose the full backend condition system.

```yaml
- custom:
    condition:
      and:
        - extension:
            values: [exe, dll]
            operator: in
            caseSensitive: false
        - size:
            operator: greaterThan
            value: 10MiB
    onMatch: [flag, quarantine]
    onNoMatch: []
```

`condition` is required. `onMatch` and `onNoMatch` are optional and default to empty.

## Boolean conditions

Literal boolean conditions are allowed:

```yaml
condition: true
```

```yaml
condition: false
```

## Logical conditions

### AND

```yaml
condition:
  and:
    - <condition>
    - <condition>
```

At least one child is required. Evaluation short-circuits.

### OR

```yaml
condition:
  or:
    - <condition>
    - <condition>
```

At least one child is required. Evaluation short-circuits.

### NOT

```yaml
condition:
  not:
    <condition>
```

## Extension

```yaml
condition:
  extension:
    values: [exe, dll, tar.gz]
    operator: in
    caseSensitive: false
```

- `values`: required string or non-empty string list.
- `operator`: optional, `in` or `notIn`; defaults to `in`.
- `caseSensitive`: optional; defaults to `false`.

Compound suffixes such as `tar.gz` are supported by the backend.

## File name

```yaml
condition:
  fileName:
    operator: glob
    pattern: "*.tmp"
    caseSensitive: true
```

`operator` is required and supports:

- `equals`
- `notEquals`
- `startsWith`
- `endsWith`
- `contains`
- `glob`
- `regex`

`caseSensitive` defaults to `true`.

## Relative path

```yaml
condition:
  path:
    operator: glob
    pattern: "uploads/**/*.zip"
    caseSensitive: true
```

The value examined is always the file path relative to the process-supplied source root, using `/` separators for matching. Operators are the same as `fileName`.

## Size

Comparison form:

```yaml
condition:
  size:
    operator: greaterThanOrEqual
    value: 10MiB
```

Supported operators:

- `equal`
- `notEqual`
- `lessThan`
- `lessThanOrEqual`
- `greaterThan`
- `greaterThanOrEqual`

Inclusive-range form:

```yaml
condition:
  size:
    betweenInclusive: [1MiB, 20MiB]
```

Sizes may be integer byte counts or strings. Supported units:

- decimal: `B`, `KB`, `MB`, `GB`, `TB`
- binary: `KiB`, `MiB`, `GiB`, `TiB`

Examples: `512`, `1500B`, `10MB`, `1.5MiB`.

## Modified time

Exactly one comparison is allowed.

Absolute comparisons use ISO-8601 instants:

```yaml
condition:
  modifiedTime:
    before: "2026-01-01T00:00:00Z"
```

Supported fields:

- `before`
- `beforeOrEqual`
- `equal`
- `afterOrEqual`
- `after`

Relative comparisons:

```yaml
condition:
  modifiedTime:
    olderThan: 30d
```

```yaml
condition:
  modifiedTime:
    newerThan: 2h
```

Duration syntax supports `ms`, `s`, `m`, `h`, `d`, `w`, or ISO-8601 durations such as `PT2H`.

## SHA-256 hash

```yaml
condition:
  hash:
    values:
      - "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    operator: in
```

- `values`: required string or non-empty list of 64-character SHA-256 hex strings.
- `operator`: `in` or `notIn`; defaults to `in`.

## MIME type

```yaml
condition:
  mimeType:
    patterns: ["image/*", "application/pdf"]
    operator: in
```

- `patterns`: required string or non-empty list.
- wildcards such as `image/*` and `*/*` are supported by the backend.
- `operator`: `in` or `notIn`; defaults to `in`.

## File signature

```yaml
condition:
  fileSignature:
    values: [PE, ELF, PDF]
    operator: in
```

Supported signatures:

- `PNG`
- `JPEG`
- `GIF`
- `PDF`
- `ZIP`
- `GZIP`
- `PE`
- `ELF`
- `MACH_O`
- `JAVA_CLASS`
- `TAR`
- `UNKNOWN`

Names are case-insensitive and `_`/`-` differences are ignored.

---

# Predefined rules

Predefined rules are configuration conveniences only. They compile into ordinary backend `Rule` and `Condition` objects, exactly like custom rules.

Every predefined rule supports:

```yaml
onMatch: ...
onNoMatch: ...
```

If omitted, predefined rules default to:

```yaml
onMatch: flag
onNoMatch: []
```

## blockExtensions

Matches when a file has one of the listed extensions.

```yaml
- predefined:
    type: blockExtensions
    extensions: [exe, dll, ps1]
    caseSensitive: false
    onMatch: [flag, quarantine]
```

## allowExtensions

Matches when the file extension is **not** in the allowlist. A file with no matching extension is therefore a match/violation.

```yaml
- predefined:
    type: allowExtensions
    extensions: [jpg, jpeg, png, pdf]
    onMatch: quarantine
```

## blockFileNames

Matches any configured filename pattern. Default operator is `glob`.

```yaml
- predefined:
    type: blockFileNames
    patterns: ["*.tmp", "desktop.ini"]
    operator: glob
    caseSensitive: false
```

## blockPaths

Matches any configured path pattern relative to the global source root. Default operator is `glob`.

```yaml
- predefined:
    type: blockPaths
    patterns: ["uploads/**/*.exe", "cache/**"]
```

## maxFileSize

Matches when size is strictly greater than `max`.

```yaml
- predefined:
    type: maxFileSize
    max: 100MiB
```

## minFileSize

Matches when size is strictly less than `min`.

```yaml
- predefined:
    type: minFileSize
    min: 1KiB
```

## blockHashes

Matches SHA-256 hashes in the supplied set.

```yaml
- predefined:
    type: blockHashes
    hashes: ["..."]
    onMatch: delete
```

## allowHashes

Matches when the file SHA-256 is not in the supplied allowlist.

```yaml
- predefined:
    type: allowHashes
    hashes: ["..."]
```

## blockMimeTypes

```yaml
- predefined:
    type: blockMimeTypes
    mimeTypes: ["application/x-msdownload", "application/x-executable"]
```

## allowMimeTypes

Matches when the detected MIME type is not accepted by the supplied patterns.

```yaml
- predefined:
    type: allowMimeTypes
    mimeTypes: ["image/*", "application/pdf"]
```

## blockSignatures

```yaml
- predefined:
    type: blockSignatures
    signatures: [PE, ELF]
    onMatch: quarantine
```

## allowSignatures

Matches when the detected file signature is not in the supplied allowlist.

```yaml
- predefined:
    type: allowSignatures
    signatures: [PNG, JPEG, PDF]
```

## olderThan

```yaml
- predefined:
    type: olderThan
    age: 90d
```

## newerThan

```yaml
- predefined:
    type: newerThan
    age: 10m
```

---

# Full example

```yaml
version: 1

ruleSets:
  - path: "."
    rules:
      - predefined:
          type: blockExtensions
          extensions: [exe, dll, ps1, bat]
          onMatch: [flag, quarantine]

      - predefined:
          type: maxFileSize
          max: 250MiB
          onMatch: flag

  - path: "uploads"
    override: true
    maxDepth: 5
    rules:
      - custom:
          condition:
            and:
              - extension:
                  values: [jpg, jpeg]
              - not:
                  fileSignature:
                    values: [JPEG]
          onMatch: [flag, quarantine]

      - custom:
          condition:
            or:
              - fileName:
                  operator: regex
                  pattern: ".*\\.(exe|scr)\\.jpg$"
                  caseSensitive: false
              - size:
                  betweenInclusive: [500MiB, 2GiB]
          onMatch: flag
          onNoMatch: []
```

Predefined/custom is not represented in the backend after compilation; both forms produce ordinary `Rule` objects and use the same runtime engine.
