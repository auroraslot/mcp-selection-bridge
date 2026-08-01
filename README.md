# Selection Bridge for Codex

Share the code you select in a JetBrains IDE with the [OpenAI Codex CLI](https://github.com/openai/codex) running in any terminal — the missing "look at what I highlighted" link for terminal-based Codex users.

[中文说明](#中文说明)

## How it works

The plugin runs a tiny [MCP](https://modelcontextprotocol.io) (streamable HTTP) server inside the IDE, listening on `127.0.0.1:63450` only. It exposes one tool:

- `get_idea_selection` — returns the file path, line range and selected text of the current editor in every open project window (focused window first).

```
┌─────────────┐  MCP over HTTP   ┌──────────────────┐
│  codex CLI  │ ───────────────► │ IDE plugin       │
│ (terminal)  │  tools/call      │ 127.0.0.1:63450  │
└─────────────┘                  └──────────────────┘
```

## Install

1. Install the plugin (JetBrains Marketplace, or *Settings | Plugins | ⚙ | Install Plugin from Disk…* with the release zip).
2. Restart the IDE.
3. Register the server with codex (once):

```bash
codex mcp add idea-selection --url http://127.0.0.1:63450/mcp
```

## Use

Select some code in the IDE, then in any codex session:

> look at the code I selected in the IDE and explain it

Codex calls `get_idea_selection` and receives your selection with file and line context.

Debug endpoints (plain JSON, useful for troubleshooting): `GET /health`, `GET /selection`.

## Settings

*Settings | Tools | Selection Bridge for Codex* — enable/disable, change port. Changes take effect after an IDE restart. If you change the port, re-register in codex with the new URL.

## Security notes

- The server binds to the loopback interface only; it is not reachable from the network.
- While the IDE is running, **any local process** can query your current selection through this port. Don't select secrets while untrusted software is running, or disable the plugin in settings.

## Compatibility

JetBrains IDEs 2026.1 – 2026.3 (IntelliJ IDEA, PyCharm, WebStorm, GoLand, …). The plugin uses platform APIs only.

## Build from source

```bash
./gradlew buildPlugin        # standard build, zip in build/distributions/
./gradlew verifyPlugin       # run JetBrains Plugin Verifier
```

Fast local iteration without Gradle (compiles against your installed IDEA's bundled JBR in ~2s):

```bash
scripts/build-with-jbr.sh    # zip in out/
```

Protocol testing without restarting the IDE — run the standalone harness with fake selection data, then point codex at it:

```bash
java -cp "out/classes:$IDEA_LIBS" life.irony.codexbridge.StandaloneHarness 63451
codex mcp add sel-test --url http://127.0.0.1:63451/mcp
```

## 中文说明

在终端里用 Codex CLI 时，让它能直接看到你在 JetBrains IDE 里框选的代码。

**原理**：插件在 IDE 内运行一个只监听 `127.0.0.1:63450` 的 MCP（streamable HTTP）服务，暴露 `get_idea_selection` 工具，返回当前各项目窗口选中的文件路径、行号与文本（聚焦窗口优先）。

**安装**：装插件 → 重启 IDE → 执行一次 `codex mcp add idea-selection --url http://127.0.0.1:63450/mcp`。

**使用**：在 IDE 里选中代码，然后在 codex 里说「看我在 IDE 里选中的代码」。

**安全提示**：服务仅监听回环地址，网络不可达；但 IDE 运行期间本机任意进程都可通过该端口读取当前选区，请知悉。可在 *Settings | Tools | Selection Bridge for Codex* 中关闭或改端口（重启生效）。

## License

[MIT](LICENSE)
