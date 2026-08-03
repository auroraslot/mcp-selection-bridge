# Selection Bridge for Codex & Kimi

Share the code you select in a JetBrains IDE with the [OpenAI Codex CLI](https://github.com/openai/codex) or the [Kimi Code CLI](https://moonshotai.github.io/kimi-code/) running in any terminal — the missing "look at what I highlighted" link for terminal-based agent users.

[中文说明](#中文说明)

## How it works

The plugin runs a tiny [MCP](https://modelcontextprotocol.io) (streamable HTTP) server inside the IDE, listening on `127.0.0.1:63450` only. It exposes one tool:

- `get_idea_selection` — returns the file path, line range and selected text of the current editor in every open project window (focused window first).

```
┌──────────────┐  MCP over HTTP   ┌──────────────────┐
│ codex / kimi │ ───────────────► │ IDE plugin       │
│  (terminal)  │  tools/call      │ 127.0.0.1:63450  │
└──────────────┘                  └──────────────────┘
```

Any other MCP client that speaks streamable HTTP works too.

## Install

1. Install the plugin (JetBrains Marketplace, or *Settings | Plugins | ⚙ | Install Plugin from Disk…* with the release zip).
2. Restart the IDE.
3. Register the server with your CLI (once):

**Codex:**

```bash
codex mcp add idea-selection --url http://127.0.0.1:63450/mcp
```

**Kimi Code** — run `/mcp-config` inside kimi and add the URL interactively, or put this in `~/.kimi-code/mcp.json` (project-level: `.kimi-code/mcp.json`):

```json
{
  "mcpServers": {
    "idea-selection": {
      "url": "http://127.0.0.1:63450/mcp"
    }
  }
}
```

(Legacy Python `kimi-cli` users: `kimi mcp add --transport http idea-selection http://127.0.0.1:63450/mcp`.)

## Use

Select some code in the IDE, then in any codex or kimi session:

> look at the code I selected in the IDE and explain it

The agent calls `get_idea_selection` and receives your selection with file and line context. In kimi the tool appears as `mcp__idea-selection__get_idea_selection`; check connection status with `/mcp`.

Debug endpoints (plain JSON, useful for troubleshooting): `GET /health`, `GET /selection`.

## Settings

*Settings | Tools | Selection Bridge for Codex & Kimi* — enable/disable, change port. Changes take effect after an IDE restart. If you change the port, re-register in your CLI with the new URL.

## Security notes

- The server binds to the loopback interface only; it is not reachable from the network.
- While the IDE is running, **any local process** can query your current selection through this port. Don't select secrets while untrusted software is running, or disable the plugin in settings.

## Compatibility

- JetBrains IDEs 2026.1 – 2026.3 (IntelliJ IDEA, PyCharm, WebStorm, GoLand, …). The plugin uses platform APIs only.
- Verified end-to-end with Codex CLI 0.146 and Kimi Code CLI 0.31 (both use streamable HTTP MCP clients).

## Build from source

```bash
./gradlew buildPlugin        # standard build, zip in build/distributions/
./gradlew verifyPlugin       # run JetBrains Plugin Verifier
```

Fast local iteration without Gradle (compiles against your installed IDEA's bundled JBR in ~2s):

```bash
scripts/build-with-jbr.sh    # zip in out/
```

Protocol testing without restarting the IDE — run the standalone harness with fake selection data, then point your CLI at it:

```bash
java -cp "out/classes:$IDEA_LIBS" life.irony.codexbridge.StandaloneHarness 63451
codex mcp add sel-test --url http://127.0.0.1:63451/mcp
# kimi: add {"mcpServers":{"sel-test":{"url":"http://127.0.0.1:63451/mcp"}}} to .kimi-code/mcp.json
```

## 中文说明

在终端里用 Codex CLI 或 Kimi Code CLI 时，让它能直接看到你在 JetBrains IDE 里框选的代码。

**原理**：插件在 IDE 内运行一个只监听 `127.0.0.1:63450` 的 MCP（streamable HTTP）服务，暴露 `get_idea_selection` 工具，返回当前各项目窗口选中的文件路径、行号与文本（聚焦窗口优先）。任何支持 streamable HTTP 的 MCP 客户端均可接入。

**安装**：装插件 → 重启 IDE → 注册一次：

- **Codex**：`codex mcp add idea-selection --url http://127.0.0.1:63450/mcp`
- **Kimi Code**：在 kimi 里执行 `/mcp-config` 交互添加，或编辑 `~/.kimi-code/mcp.json`（项目级为 `.kimi-code/mcp.json`）：

  ```json
  { "mcpServers": { "idea-selection": { "url": "http://127.0.0.1:63450/mcp" } } }
  ```

  （旧版 Python kimi-cli：`kimi mcp add --transport http idea-selection http://127.0.0.1:63450/mcp`。）

**使用**：在 IDE 里选中代码，然后在 codex / kimi 里说「看我在 IDE 里选中的代码」。kimi 里工具名显示为 `mcp__idea-selection__get_idea_selection`，可用 `/mcp` 查看连接状态。

**安全提示**：服务仅监听回环地址，网络不可达；但 IDE 运行期间本机任意进程都可通过该端口读取当前选区，请知悉。可在 *Settings | Tools | Selection Bridge for Codex & Kimi* 中关闭或改端口（重启生效）。

**兼容性**：已用 Codex CLI 0.146 与 Kimi Code CLI 0.31 双端实测验证。

## License

[MIT](LICENSE)
