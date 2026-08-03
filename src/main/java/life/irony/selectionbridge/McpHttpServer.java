package life.irony.selectionbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内置的 MCP streamable HTTP 服务，只监听 127.0.0.1。
 * 行为对齐 JetBrains 内置 MCP server（已验证可与 codex CLI 的 rmcp 客户端互通）：
 *  - POST /mcp：JSON-RPC 请求，响应用 SSE 格式（event: message + data: 行），带 Mcp-Session-Id 头
 *  - POST 通知（无 id）：202 无响应体
 *  - GET  /mcp：SSE 长连接，仅周期性注释行保活
 *  - DELETE /mcp：204
 * 另保留 GET /selection、GET /health 两个调试端点。
 *
 * codex 侧注册：codex mcp add idea-selection --url http://127.0.0.1:63450/mcp
 * kimi  侧注册：kimi 内执行 /mcp-config，或在 ~/.kimi-code/mcp.json 的 mcpServers 里
 *              加 {"idea-selection": {"url": "http://127.0.0.1:63450/mcp"}}
 * （已对 codex 0.146 与 Kimi Code 0.31 双端实测互通，两者均为 streamable HTTP 客户端。）
 */
public final class McpHttpServer {
    public static final String TOOL_NAME = "get_idea_selection";
    private static final String SERVER_VERSION = "1.1.0";
    private static final long KEEPALIVE_MILLIS = 25_000L;

    private static final Gson GSON = new Gson();

    private final int port;
    private final SelectionProvider provider;
    private HttpServer server;
    private ExecutorService executor;

    public McpHttpServer(int port, SelectionProvider provider) {
        this.port = port;
        this.provider = provider;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-selection-bridge");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/mcp", this::handleMcp);
        server.createContext("/health", ex -> sendJson(ex, "{\"ok\":true}"));
        server.createContext("/selection", ex -> sendJson(ex, selectionDebugJson()));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    public int getPort() {
        return port;
    }

    // ---------- /mcp ----------

    private void handleMcp(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            if ("POST".equals(method)) {
                handlePost(ex);
            } else if ("GET".equals(method)) {
                handleGetStream(ex);
            } else if ("DELETE".equals(method)) {
                ex.sendResponseHeaders(204, -1);
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            // 协议层兜底：任何异常都不能带崩 HTTP 线程
            try {
                ex.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        JsonObject req;
        try (InputStream in = ex.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                sendRpcError(ex, null, -32600, "batch requests are not supported");
                return;
            }
            req = parsed.getAsJsonObject();
        } catch (Exception e) {
            sendRpcError(ex, null, -32700, "parse error");
            return;
        }

        if (!req.has("id")) { // notification：确认即可，无响应体
            ex.sendResponseHeaders(202, -1);
            return;
        }

        JsonElement id = req.get("id");
        String method = req.has("method") ? req.get("method").getAsString() : "";
        switch (method) {
            case "initialize": {
                JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();
                String pv = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "2025-06-18";
                JsonObject result = new JsonObject();
                result.addProperty("protocolVersion", pv);
                JsonObject caps = new JsonObject();
                caps.add("tools", new JsonObject());
                result.add("capabilities", caps);
                JsonObject info = new JsonObject();
                info.addProperty("name", "mcp-selection-bridge");
                info.addProperty("version", SERVER_VERSION);
                result.add("serverInfo", info);
                sendRpcResult(ex, id, result, UUID.randomUUID().toString());
                break;
            }
            case "tools/list": {
                JsonObject result = new JsonObject();
                JsonArray tools = new JsonArray();
                tools.add(toolDefinition());
                result.add("tools", tools);
                sendRpcResult(ex, id, result, null);
                break;
            }
            case "tools/call": {
                JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();
                String name = params.has("name") ? params.get("name").getAsString() : "";
                if (!TOOL_NAME.equals(name)) {
                    sendRpcError(ex, id, -32602, "unknown tool: " + name);
                    return;
                }
                JsonObject result = new JsonObject();
                JsonArray content = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", renderSelectionText());
                content.add(text);
                result.add("content", content);
                result.addProperty("isError", false);
                sendRpcResult(ex, id, result, null);
                break;
            }
            case "ping": {
                sendRpcResult(ex, id, new JsonObject(), null);
                break;
            }
            default:
                sendRpcError(ex, id, -32601, "method not found: " + method);
        }
    }

    private static JsonObject toolDefinition() {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", TOOL_NAME);
        tool.addProperty("description",
                "Get the code currently selected in the user's JetBrains IDE editor "
                        + "(file path, line range, selected text). Call this whenever the user refers to "
                        + "\"the selected code\", \"this code\", or code highlighted in the IDE. "
                        + "获取用户当前在 IDE 编辑器中选中的代码。");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        tool.add("inputSchema", schema);
        return tool;
    }

    // ---------- SSE 长连接（rmcp 客户端会主动开一条 GET 流） ----------

    private static void handleGetStream(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, 0);
        OutputStream os = ex.getResponseBody();
        try {
            while (true) {
                os.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                Thread.sleep(KEEPALIVE_MILLIS);
            }
        } catch (IOException | InterruptedException ignored) {
            // 客户端断开或服务停止
        }
    }

    // ---------- 响应编码 ----------

    private static void sendRpcResult(HttpExchange ex, JsonElement id, JsonObject result, String sessionId)
            throws IOException {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.add("id", id);
        msg.add("result", result);
        sendSse(ex, msg, sessionId);
    }

    private static void sendRpcError(HttpExchange ex, JsonElement id, int code, String message)
            throws IOException {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.add("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        msg.add("error", error);
        sendSse(ex, msg, null);
    }

    private static void sendSse(HttpExchange ex, JsonObject msg, String sessionId) throws IOException {
        byte[] bytes = ("event: message\ndata: " + GSON.toJson(msg) + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        if (sessionId != null) {
            ex.getResponseHeaders().add("Mcp-Session-Id", sessionId);
        }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
        ex.close();
    }

    // ---------- 选区渲染 ----------

    private String selectionDebugJson() {
        JsonObject root = new JsonObject();
        root.add("editors", GSON.toJsonTree(provider.currentSelections()));
        return GSON.toJson(root);
    }

    private String renderSelectionText() {
        List<EditorSelection> editors = provider.currentSelections();
        if (editors.isEmpty()) {
            return "The IDE is connected but no editor is open.";
        }
        StringBuilder sb = new StringBuilder();
        boolean hasSelection = false;
        for (EditorSelection ed : editors) {
            if (sb.length() > 0) sb.append("\n\n");
            String tag = ed.focused ? " (focused window)" : "";
            sb.append("Project: ").append(ed.project).append(tag)
              .append("\nFile: ").append(ed.file);
            if (ed.selectedText != null && !ed.selectedText.isEmpty()) {
                hasSelection = true;
                String fence = ed.selectedText.contains("```") ? "````" : "```";
                sb.append("\nSelection: lines ").append(ed.startLine).append("-").append(ed.endLine)
                  .append("\n").append(fence).append(languageHint(ed.file))
                  .append("\n").append(ed.selectedText)
                  .append("\n").append(fence);
            } else {
                sb.append("\nNo text selected (caret at line ").append(ed.startLine).append(").");
            }
        }
        if (!hasSelection) {
            return "No code is currently selected in the IDE. Open editors:\n\n" + sb;
        }
        return sb.toString();
    }

    private static String languageHint(String file) {
        if (file == null) return "";
        int dot = file.lastIndexOf('.');
        if (dot < 0 || dot == file.length() - 1) return "";
        String ext = file.substring(dot + 1);
        return ext.matches("[A-Za-z0-9]{1,12}") ? ext.toLowerCase() : "";
    }
}
