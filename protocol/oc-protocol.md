English | [简体中文](oc-protocol_zh_CN.md)

# OC/1 Protocol

> This is the single wire contract between the browser client and the Java server. The implementations on both sides must obey this document; it records no implementation history and describes no particular client.

## What this document covers

- Transport: the WebSocket path, one command per text frame, the `HELLO 1` version handshake.
- The command table, and the exact answer to every command.
- `REQ`'s login precondition, the base64 convention, and the error vocabulary.
- The second layer of `oc-debug`: byte layout, key, and the 60-second anti-replay window.
- Plugin assets: ordinary HTTP content under `/plugin-assets`, not part of the command protocol.
- Version compatibility rules, and how to start the browser client with pnpm.

## Transport

| Item | Contract |
| --- | --- |
| WebSocket address | `ws://<host>:25567/ws` |
| HTTP address | `http://<host>:25567/`, serving the static files of the same `web-client/dist/` plus the plugin assets under `/plugin-assets` |
| Frame type | **TEXT** frames only; a binary frame is not a command, so the server answers ERR and keeps the connection open, and the client ignores it and shows a notice |
| Frame content | Each TEXT frame carries **exactly one command line**, UTF-8, without CR/LF |
| Answer | **Exactly one line** of answer per command; the client must not pack several commands into one frame |
| Close | On the normal path only `QUIT` closes the connection; a failed handshake closes it; on a timeout or a frame-rule violation an implementation may close it, with a readable reason |

A client cannot send commands right after connecting: **the first frame must be the version handshake** (see the next section). The server accepts no other command before a successful handshake. Inside a command line the command word (`PING`, `REQ`, and so on) is **case-insensitive**; arguments are passed as-is, with no case folding.

## Version handshake

After the upgrade to WebSocket, the first frame the client sends must be:

```text
HELLO 1
```

The server answers:

```text
OK OC/1
```

- The number after `HELLO` is the protocol version, currently `1`; `OC/1` is the full name of that same version.
- When the first frame is not `HELLO 1` (including `HELLO 2`, `HELLO`, or a command sent directly), the server **closes the connection** with an explicit error message and executes no command.
- When what the client receives is not `OK OC/1`, it must treat the connection as unusable and must not keep sending commands.

On a failed handshake the server answers one error line first and then closes; the client only has to see that it is not `OK OC/1`, and does not need to depend on the exact wording:

| Error line | Trigger |
| --- | --- |
| `ERR HELLO first` | The first frame is not `HELLO` |
| `ERR malformed HELLO` | `HELLO` has no version number, has extra arguments, or the version number is not a decimal number |
| `ERR unsupported version, this server speaks OC/1` | The version number is not `1` |

The handshake's version number and this document's version number are one and the same thing: when this document changes, the version number changes (see "Version compatibility").

## Command table

After a successful handshake, one line per command, one line of answer per command:

| Command | Answer | Description |
| --- | --- | --- |
| `PING` | `PONG` | Connectivity probe; changes nothing |
| `REGISTER <name> <password>` | `OK <message>` or `ERR <message>` | Registers an account; the success and failure texts come from the server |
| `LOGIN <name> <password>` | `OK <message>` or `ERR <message>` | Authenticates **this connection**; only after success may this connection send `REQ` |
| `COUNT` | `OK <n>` | Number of registered accounts; `n` is a decimal non-negative integer |
| `REQ <name> <base64-payload>` | `OK <base64-payload>` or `ERR <reason>` | Calls a registered read-only request; requires `LOGIN` on the same connection first |
| `QUIT` | `OK bye` | The server sends this line first, then closes the connection |

`REGISTER` and `LOGIN` answer with a human-readable `<message>`; the client may rely only on the leading `OK` / `ERR` to judge success or failure, and must not parse or compare the whole sentence.
For `COUNT` the `n` is the only payload; the answer to `QUIT` is exactly `OK bye`.

### `REQ` rules

`REQ` requires a **successful `LOGIN` on the same connection**. `REQ`'s `<name>` is the registered name, matched exactly as-is; `engine-info` is
all lowercase, and `ENGINE-INFO` is not folded into it. The failure answers are fixed short sentences, in this priority:

| Case | Answer |
| --- | --- |
| This connection has not logged in successfully yet | `ERR login first` |
| The name is not registered (or the name is invalid, the payload is missing, or the payload is not valid base64) | `ERR unknown request` |
| The handler throws, or returns `null` | `ERR request failed` |
| Any other rejection decided by the handler | `ERR <reason>` |

On success the answer is `OK <base64-payload>`. The server only base64-decodes the request payload into **opaque bytes** for the handler,
then base64-encodes the bytes the handler returns and sends them back; it does not parse the payload, does not record the payload content, and does not understand the `oc-debug` envelope.

`REQ` is the only command in the protocol that requires login. `PING`, `COUNT`, `REGISTER`, `LOGIN`, and `QUIT` require no login.

`hello` is the sample request provided by the `oc-hello` plugin: the payload is an ordinary UTF-8 name (not an envelope like `oc-debug`'s), and the answer is ordinary UTF-8 text as well.

## base64 convention

- Use the standard alphabet of RFC 4648 section 4 (`A`–`Z`, `a`–`z`, `0`–`9`, `+`, `/`), with `=` padding.
- Do not use base64url (no `-` and `_`), and do not insert whitespace, line breaks, or a prefix.
- The `<base64-payload>` in a command must decode successfully, otherwise the server answers `ERR unknown request` before the handler is reached.
- The `<base64-payload>` in an answer is the encoding of the **raw bytes**; the client must decode first, then interpret them in the request's own format. The `oc-debug` envelope is binary, so as far as the browser is concerned base64 exists only so that the envelope can travel through one line of text.

## The `engine-info` envelope

The request name registered by the `oc-debug` plugin is `engine-info`. It requires login and is called under the normal `REQ` rules; in addition it seals the request and the answer
in a second-layer AES-256-GCM envelope. The server treats this layer as **opaque base64 bytes**; only the key-holding plugin and the client
open it in the format below.

### Key

- The key is 32 bytes (AES-256), taken from the server's `data/oc-debug/debug.key`.
- The content of `debug.key` is 64 hexadecimal characters (either case accepted), optionally with one trailing newline; the browser parses it into `Uint8Array(32)`.
- The file is generated when the server first enables the plugin and is **never overwritten**; the operator copies it to the client machine **by hand**, the way one copies an SSH private key. The key never travels over the network and is never generated by the browser.
- When the key is not 32 bytes long, or the text is not 64 hexadecimal characters, the client must reject it locally, and must not silently pad or hash it.

### Byte layout

An envelope is the following bytes concatenated in order:

| Offset | Length | Field | Content |
| --- | --- | --- | --- |
| 0 | 6 | magic | ASCII `OCDBG1`, fixed and unchanging |
| 6 | 8 | timestamp | unsigned big-endian epoch seconds (UTC) |
| 14 | 12 | nonce | random bytes freshly taken for each envelope, which must not repeat under the same key |
| 26 | n | ciphertext | AES-256-GCM ciphertext, with a 16-byte (128-bit) authentication tag at the end |

- The fixed header is 26 bytes; the shortest envelope is 42 bytes (26 + the 16-byte tag).
- The key is 32 bytes, the algorithm is fixed to AES-256-GCM, and no AAD is used (the additional authenticated data is empty).
- GCM authenticates the ciphertext and the nonce; magic and timestamp are checked separately before the cryptographic operation runs (a magic mismatch is rejected outright, an out-of-window timestamp is rejected outright).
- nonce is random rather than a counter: the two ends share no counter, the server may restart, and the client may change machines.

### Plaintext

The plaintext inside the envelope is one line of UTF-8 text, which `oc-debug` handles by **exact comparison**:

| Plaintext request | Plaintext answer |
| --- | --- |
| `INFO` | text report, one `key: value` per line |
| `INFO json` | JSON object of the same report |
| anything else | `ERR unsupported request` |
| the envelope cannot be opened (magic, length, tag, clock) | `ERR bad request` |

- The plaintext is stripped of leading and trailing whitespace before comparison; the comparison is case-sensitive, so neither `info` nor `INFO JSON` is accepted.
- When the envelope cannot be opened, the plugin still seals an envelope whose plaintext is `ERR bad request` back with the **same key**. So what the transport layer sees is still `OK <base64>`; the error is inside the envelope.
- The report fields belong to `oc-debug`'s report format, not to the transport protocol; the client should tolerate unknown fields and show only the ones it knows, or show the text as-is.

### The 60-second anti-replay window

timestamp is the only anti-replay element in the envelope, and the reader must check it in **both directions**:

- An envelope timestamp more than 60 seconds earlier than the local clock: reject, treating it as an old replayed request.
- An envelope timestamp more than 60 seconds later than the local clock: reject, treating it as a clock error.
- Exactly 60 seconds apart: **accept** (the boundary counts as inside the window).

That is the whole meaning of "the 60-second window". It is a bounded defense: clocks drift, so the window cannot be zero; and precisely because it is bounded, `engine-info` can only be a stateless report, never a command with side effects.

### Browser implementation notes

`sealEnvelope` and `openEnvelope` implement AES-256-GCM with WebCrypto's `crypto.subtle`.
Browsers provide `crypto.subtle` only in a **secure context**:

- `http://127.0.0.1:25567/` and `http://localhost:5173/` are secure contexts and work.
- When the page is opened directly at `http://<LAN IP>:25567/`, the browser usually does **not** provide `crypto.subtle`, and then
  `engine-info` cannot work; use localhost, an SSH port forward, or an HTTPS reverse proxy.

## Error vocabulary

Transport-layer error answers all start with `ERR `, and one command gets only one line:

| Answer | Meaning |
| --- | --- |
| `ERR login first` | This connection has not successfully `LOGIN`ed, so `REQ` was rejected |
| `ERR unknown request` | `REQ`'s name is not registered, or the request shape/base64 is invalid |
| `ERR request failed` | The handler threw, or returned `null` |
| `ERR <message>` / `ERR <reason>` | Other rejections of `REGISTER`, `LOGIN`, `REQ`, with wording given by the server or the handler |

Errors inside the envelope are another layer, and never become transport-layer errors:

| Plaintext answer | Meaning |
| --- | --- |
| `ERR unsupported request` | The envelope opened, but the plaintext is not `INFO` or `INFO json` |
| `ERR bad request` | The envelope did not open: magic, length, tag, or time window invalid |

The client must report these two layers separately: `ERR bad request` usually means the `debug.key` is wrong or the two machines' clocks differ too much, and `ERR unsupported request` usually means the request line was written wrong.

## Plugin assets

Textures and other plugin assets are **ordinary HTTP content**, not OC/1 messages: the client reads them with `fetch`, they have nothing to do with the WebSocket, and they need no login.

| Route | Content |
| --- | --- |
| `GET /plugin-assets/manifest.json` | JSON array formed by merging the atlas manifests of all loaded plugins |
| `GET /plugin-assets/<plugin id>/<file>` | the raw bytes of `assets/<file>` inside that plugin's jar |

- The manifest is an array, and its order is the plugin load order; each element is
  `{"plugin": "<plugin id>", "atlasUrl": "...", "tilePixels": ..., "tiles": {...}, "entities": {...}}`.
  `atlasUrl` is rewritten by the server from the `image` in the plugin manifest; the plugin manifest itself contains no absolute URL.
- When no plugin publishes a manifest, the manifest route answers `200` and `[]`, not `404`.
- The manifest's `Content-Type` is `application/json; charset=utf-8`; files get a MIME type by extension (`image/png`,
  `application/json`, and so on), and an unknown extension is `application/octet-stream`. Both carry `Content-Length`,
  and always `Cache-Control: no-cache`.
- Only `GET` and `HEAD` are allowed; other methods answer `405`, with `Allow: GET, HEAD`.
- A path containing `..`, an encoded dot (`%2e`), a backslash, NUL, a drive letter, or an empty plugin id answers `400`; a path that normalizes outside `assets/` answers
  `403`; an unknown plugin, a missing file, or a plugin without `assets/` all answer `404`.
- The server reads resources only through the plugin's own class loader, and never touches the plugin directory on disk for this route, so a plugin cannot use it to read server files.
- This route shares the `--http-port` port with the static pages; with `--http-port 0` neither exists.
- **It changes no command, answer, frame rule, or version number in this document.** When the client cannot get the atlas it falls back to procedural drawing, and the protocol panel works as usual.
- When several plugins provide the same terrain or entity, the client lets the **later one override the earlier** in array order; for the manifest format see
  [`web-client/public/textures/README.md`](../web-client/public/textures/README.md).

## Version compatibility

**This document's version number is the protocol's version number.** The current version is `1`, written on the wire as `HELLO 1` and `OK OC/1`.

- When this document changes the frame rules, the command table, any answer, the base64 convention, the envelope layout, the key length, the window length, or the magic, the version number must increase (`2`, `OK OC/2`, and so on), and this document, the server, and the client must be changed together.
- The version number is the **only** basis on which the two ends judge compatibility; do not guess compatibility from "the field still looks present" or "the string still parses".
- Adding report fields only, or changing only the human-readable text of `REGISTER`/`LOGIN`, is not a protocol change; the client should not depend on that content anyway.
- An old client meeting a new version receives a handshake answer other than `OK OC/1` and disconnects under the handshake rules; a new client meeting an old server does the same. This is the deliberate failure mode: refusing is safer than half-compatibility.

## How to start the client with pnpm

The browser client is in `web-client/` and is completely parallel to Gradle: Gradle **never** calls npm/pnpm, and pnpm **never** calls Gradle.

Prerequisites:

| Requirement | Version/notes |
| --- | --- |
| Node.js | 18+ (required by Vite 6; 20+ recommended) |
| pnpm | 9+; `web-client/package.json` says `packageManager: "pnpm@9"` |
| Network | **the first `pnpm install` needs network**; dev/build after installation can be offline |
| Java server | on `25567` serves `/ws` and the static pages; start it per the server documentation first |

The dependency range is `typescript ^6.0.3` and `vite ^6.0.0` (resolving to Vite 6.x), see `web-client/package.json`.

```shell
cd web-client
pnpm install
pnpm dev
```

Open the address Vite prints. During development, leave **both** the host and the port on the page empty, and the client connects to the same-origin `/ws`, which Vite's proxy forwards to `ws://127.0.0.1:25567`; fill in host/port to connect directly.

Build:

```shell
pnpm build
```

The output is in `web-client/dist/`, served by the Java server at `http://<host>:25567/`.
A failed build does not affect the Java side: `./gradlew build --offline` still compiles only Java, needs no Node, and does not call pnpm.

`engine-info` requires copying the server's `data/oc-debug/debug.key` by hand into the text box on the page; do not commit the key into the repository.

## Related documents

- Browser client usage notes: [`web-client/README.md`](../web-client/README.md)
- Repository overview and parallel toolchains: [`README.md`](../README.md)
