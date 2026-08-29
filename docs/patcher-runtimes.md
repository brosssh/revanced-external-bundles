# Isolated patcher runtimes

Patch bundles are loaded in worker JVMs instead of the Ktor process. Each runtime has its own process and classpath,
allowing incompatible patcher versions to run together.

## Configuration

[`src/main/resources/patcher-runtimes.toml`](../src/main/resources/patcher-runtimes.toml) defines the worker settings
and runtime selection rules used by both Gradle and the backend. All `[worker]` settings are required.

A `runtimes` entry maps an installed patcher runtime to the bundle-declared `Patcher-Version` values it accepts. Runtime
keys and `fallback-runtimes` entries use full Maven coordinates. Fallbacks are tried in order when a bundle has no
`Patcher-Version`.

```toml
[bundle-types."Morphe:V1"]
adapter = "morphe-v1"
fallback-runtimes = ["app.morphe:morphe-patcher:1.1.1"]

[bundle-types."Morphe:V1".runtimes]
"app.morphe:morphe-patcher:1.2.0" = "<=1.2.0"
```

Bundle release versions are not used for runtime selection. They are not globally unique and do not describe patcher
compatibility.

Declared patcher versions must follow Semantic Versioning. Compatibility ranges
use [semver4j](https://github.com/semver4j/semver4j) and support prereleases. Examples:

- `*`
- `<2` or `<=2`
- `>=1`
- `>=1 <2`
- `=1.3.3`
- `1.x || >=3`

Ranges for the same bundle type must not overlap. Fallback lists must not be empty or contain duplicate coordinates. The
backend also checks that every configured runtime directory exists. Invalid configuration stops startup.

Gradle uses the same application parser and registry, so build-time and startup validation follow the same rules.

### Runtime selection

- `Morphe:V1` reads `Patcher-Version` from the bundle manifest. A declared version selects one matching runtime. Bundles
  without the key use the fallback list.
- `ReVanced:V3` and `ReVanced:V4` do not include patcher metadata and always use their fallback lists.

Current fallback order:

- `ReVanced:V3`: `19.3.1`, `15.0.3`, `11.0.4`
- `ReVanced:V4`: `22.1.0-dev.1`, `21.1.0-dev.5`, `20.0.2`

Each entry is needed for at least one stored bundle. Patcher 20 and 21 expose `PatchKt.loadPatchesFromJar`, while
Patcher 22 exposes `PatchKt.getPatches`.

A successful extraction stores the full runtime coordinate in `bundle.patcher_runtime`. If that runtime remains
configured, it is tried first the next time the bundle is extracted. Patch replacement, runtime caching, failure
cleanup, and clearing `need_patches_update` occur in one transaction.

When every applicable runtime rejects a bundle, the backend stores the failure and a fingerprint of the
runtime-selection rules, then clears `need_patches_update`. A changed artifact or selection fingerprint requeues the
bundle; an operator can also requeue it manually. Download, timeout, worker, and transport failures remain queued for
retry.

## Build and deployment

Prepare the configured runtimes with:

```shell
./gradlew preparePatcherRuntimes
```

`generatePatcherRuntimeManifest` parses the TOML through the application config code and writes
`build/generated/patcher-runtimes/manifest.json`. The `buildSrc` plugin reads that manifest and resolves each runtime
into `build/patcher-runtimes/<bundle-type>/<version>/`.

The `run`, `shadowJar`, `startShadowScript`, and test tasks depend on runtime preparation. The Docker build copies the
runtime directories to `/app/patcher-runtimes` and sets `BACKEND_PATCHER_RUNTIME_DIR`. Maven access is only required
while building the image. Changing a runtime requires a new image.

## Worker lifecycle

Workers start when needed and are reused until their idle timeout. Requests to one process are serialized, but a busy
runtime may use more than one process.

Pool limits are configured separately from the runtime compatibility table:

| Environment variable                     | Default | Meaning                                      |
|------------------------------------------|--------:|----------------------------------------------|
| `BACKEND_PATCHER_WORKER_MAX_PROCESSES`   |       4 | Maximum patcher JVMs across all runtimes     |
| `BACKEND_PATCHER_WORKER_MAX_PER_RUNTIME` |       2 | Maximum JVMs for one runtime                 |
| `BACKEND_PATCHER_WORKER_IDLE_SECONDS`    |     300 | Idle time before a worker is stopped         |
| `BACKEND_PATCHER_REFRESH_CONCURRENCY`    |       4 | Bundles downloaded and extracted at one time |

When the global limit is reached, the pool reclaims an idle runtime slot or waits for a worker. Each process uses the
heap limit from `[worker]` (`-Xmx512m` by default). With the default process limit, the maximum patcher heap allocation
is 2 GiB.

## Worker protocol

The backend and workers communicate through a versioned, length-prefixed binary protocol over stdin and stdout. A
request contains an ID and the raw bundle bytes. The response contains one of these statuses:

- `SUCCESS` with patch snapshots
- `BUNDLE_REJECTED` when the selected adapter cannot load the bundle
- `WORKER_FAILURE` when processing fails outside bundle loading

`BUNDLE_REJECTED` also applies when the bundle references a dependency that is not on the worker classpath. The backend
downloads release assets directly and does not resolve dependencies declared by the bundle project.

Paths are not shared between processes. A worker writes the received bytes to its own temporary file because the patcher
APIs require a file. Logs are written to stderr so they cannot corrupt protocol frames.

`WORKER_FAILURE`, process, and transport failures restart the worker up to `[worker].restart-attempts` times. The
request timeout is a single deadline shared by all attempts, so a timeout is not retried. Adapter and linkage failures
return `BUNDLE_REJECTED` without restarting the process. A bundle is marked exhausted only when every applicable
runtime rejects it. Existing patch rows are kept on failure.

Downloads and patch parsing run outside database transactions. Only patch replacement holds a database connection. A
coroutine semaphore limits concurrent extraction, and a per-bundle mutex prevents overlapping jobs from replacing the
same bundle at the same time.

Worker isolation prevents patcher dependency conflicts and keeps ordinary patcher crashes out of the Ktor process. It is
not a security sandbox; loading a bundle may execute its class initializers in the worker.
