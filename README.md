# MCP Selection Bridge

把你在 JetBrains IDE 里框选的代码，直接共享给终端里的 [Codex CLI](https://github.com/openai/codex)、[Kimi Code CLI](https://moonshotai.github.io/kimi-code/) 或任何支持 MCP 的智能体——补上终端 AI 用户一直缺的那句「看我高亮的这段」。

[English](#english) · [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/33281)

## 原理

插件在 IDE 内运行一个极小的 [MCP](https://modelcontextprotocol.io)（streamable HTTP）服务，仅监听 `127.0.0.1:63450`，对外只暴露一个工具：

- `get_idea_selection` —— 返回当前各个项目窗口中选中内容的文件路径、行号范围与文本（聚焦窗口优先）。

```
┌──────────────┐  MCP over HTTP   ┌──────────────────┐
│ codex / kimi │ ───────────────► │ IDE 插件          │
│   （终端）    │  tools/call      │ 127.0.0.1:63450  │
└──────────────┘                  └──────────────────┘
```

任何支持 streamable HTTP 的 MCP 客户端同样可以接入。

## 安装

1. 装插件（JetBrains Marketplace，或用 release 里的 zip 走 *Settings | Plugins | ⚙ | Install Plugin from Disk…*）。
2. 重启 IDE。
3. 注册一次：打开 *Settings | Tools | MCP Selection Bridge*，点 **Register in Codex** / **Register in Kimi Code** 按钮即可。

想手动注册也可以：

**Codex：**

```bash
codex mcp add idea-selection --url http://127.0.0.1:63450/mcp
```

**Kimi Code：**在 kimi 里执行 `/mcp-config` 交互添加，或写入 `~/.kimi-code/mcp.json`（项目级为 `.kimi-code/mcp.json`）：

```json
{
  "mcpServers": {
    "idea-selection": {
      "url": "http://127.0.0.1:63450/mcp"
    }
  }
}
```

（旧版 Python `kimi-cli` 用户：`kimi mcp add --transport http idea-selection http://127.0.0.1:63450/mcp`。）

## 使用

在 IDE 里选中代码，然后在 codex 或 kimi 会话里说：

> 看我在 IDE 里选中的代码，讲讲这段

智能体会调用 `get_idea_selection`，拿到你的选区以及所属文件和行号上下文。在 kimi 里工具名显示为 `mcp__idea-selection__get_idea_selection`，可用 `/mcp` 查看连接状态。

排查问题时可用的调试端点（返回纯 JSON）：`GET /health`、`GET /selection`。

## 设置

*Settings | Tools | MCP Selection Bridge* —— 开关、改端口、一键注册按钮。改动需重启 IDE 生效。改了端口后请重新点一次注册按钮（会覆盖旧条目），或按新地址手动注册。

## 安全说明

- 服务只绑定回环地址，外部网络不可达。
- 但 IDE 运行期间，**本机任意进程**都能通过该端口读取你当前的选区。别在有不可信软件运行时选中敏感内容，或直接在设置里关掉插件。

## 兼容性

- JetBrains IDE 2026.1 – 2026.3（IntelliJ IDEA、PyCharm、WebStorm、GoLand 等）。插件只使用平台公共 API。
- 已用 Codex CLI 0.146 与 Kimi Code CLI 0.31 双端实测验证（两者都是 streamable HTTP 的 MCP 客户端）。

## 从源码构建

```bash
./gradlew buildPlugin        # 标准构建，zip 产物在 build/distributions/
./gradlew verifyPlugin       # 跑 JetBrains Plugin Verifier
```

本地快速迭代（不走 Gradle，直接用本机 IDEA 自带 JBR 编译，约 2 秒）：

```bash
scripts/build-with-jbr.sh    # zip 产物在 out/
```

调协议时不想反复重启 IDE，可以跑独立测试进程（返回假的选区数据），再把 CLI 指过去：

```bash
java -cp "out/classes:$IDEA_LIBS" life.irony.selectionbridge.StandaloneHarness 63451
codex mcp add sel-test --url http://127.0.0.1:63451/mcp
# kimi：把 {"mcpServers":{"sel-test":{"url":"http://127.0.0.1:63451/mcp"}}} 写进 .kimi-code/mcp.json
```

## English

Share the code you select in a JetBrains IDE with the [OpenAI Codex CLI](https://github.com/openai/codex), the [Kimi Code CLI](https://moonshotai.github.io/kimi-code/) or any other MCP-capable agent running in a terminal.

**How it works** — the plugin runs a tiny [MCP](https://modelcontextprotocol.io) (streamable HTTP) server inside the IDE, bound to `127.0.0.1:63450` only. It exposes one tool, `get_idea_selection`, returning the file path, line range and selected text of the current editor across all open project windows (focused window first). Any MCP client that speaks streamable HTTP works.

**Install** — install the plugin, restart the IDE, then open *Settings | Tools | MCP Selection Bridge* and click **Register in Codex** / **Register in Kimi Code**. Manually:

```bash
codex mcp add idea-selection --url http://127.0.0.1:63450/mcp
```

For Kimi Code, run `/mcp-config` inside kimi, or add `{"mcpServers":{"idea-selection":{"url":"http://127.0.0.1:63450/mcp"}}}` to `~/.kimi-code/mcp.json`.

**Use** — select code in the IDE, then tell your agent *"look at the code I selected in the IDE"*.

**Security** — the server binds to loopback only and is not reachable from the network, but while the IDE is running **any local process** can query your current selection. Disable the plugin or change the port in settings.

**Compatibility** — JetBrains IDEs 2026.1 – 2026.3, platform APIs only. Verified end-to-end with Codex CLI 0.146 and Kimi Code CLI 0.31.

## License

[MIT](LICENSE)
