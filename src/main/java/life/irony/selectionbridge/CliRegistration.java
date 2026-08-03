package life.irony.selectionbridge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 一键把本服务注册进 codex / kimi CLI。纯 JDK + Gson 实现，不依赖 IDE API，
 * 便于脱离 IDE 自测（同 StandaloneHarness 的思路）。
 *  - codex：调用 codex mcp add（官方 CLI 自己写 config.toml，同名条目直接覆盖，已实测幂等）
 *  - kimi ：合并写 ~/.kimi-code/mcp.json（保留已有条目；解析失败绝不覆盖原文件）
 */
public final class CliRegistration {
    public static final String SERVER_NAME = "idea-selection";
    private static final long PROCESS_TIMEOUT_SECONDS = 15;

    private CliRegistration() {
    }

    public static String mcpUrl(int port) {
        return "http://127.0.0.1:" + port + "/mcp";
    }

    // ---------- kimi：合并写 mcp.json ----------

    /** 把本服务合并写入 kimi 的用户级 mcp.json，返回写入的文件路径。 */
    public static Path registerKimi(int port) throws IOException {
        Path dir = kimiHome();
        if (!Files.isDirectory(dir)) {
            throw new IOException("Kimi Code config directory not found: " + dir
                    + " — is Kimi Code installed?");
        }
        Path file = dir.resolve("mcp.json");
        JsonObject root = readJsonObjectOrEmpty(file);
        JsonObject servers;
        JsonElement existing = root.get("mcpServers");
        if (existing == null) {
            servers = new JsonObject();
            root.add("mcpServers", servers);
        } else if (existing.isJsonObject()) {
            servers = existing.getAsJsonObject();
        } else {
            throw new IOException("\"mcpServers\" in " + file + " is not a JSON object; refusing to modify it.");
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("url", mcpUrl(port));
        servers.add(SERVER_NAME, entry);

        String out = new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
        Path tmp = Files.createTempFile(dir, "mcp", ".json.tmp");
        try {
            Files.writeString(tmp, out, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return file;
    }

    private static JsonObject readJsonObjectOrEmpty(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        String old = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (old.isEmpty()) return new JsonObject();
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(old);
        } catch (Exception e) {
            throw new IOException(file + " is not valid JSON; refusing to modify it. Fix or remove it first.");
        }
        if (!parsed.isJsonObject()) {
            throw new IOException(file + " does not contain a JSON object; refusing to modify it.");
        }
        return parsed.getAsJsonObject();
    }

    private static Path kimiHome() {
        String env = System.getenv("KIMI_CODE_HOME");
        return env != null && !env.isBlank()
                ? Path.of(env)
                : Path.of(System.getProperty("user.home"), ".kimi-code");
    }

    // ---------- codex：调用 codex mcp add ----------

    /** 调用 codex mcp add 注册本服务，返回所用的 codex 可执行文件路径。 */
    public static String registerCodex(int port) throws IOException, InterruptedException {
        String codex = findCodex();
        if (codex == null) {
            throw new IOException("codex executable not found");
        }
        List<String> cmd = new ArrayList<>();
        if (codex.toLowerCase(Locale.ROOT).endsWith(".cmd") || codex.toLowerCase(Locale.ROOT).endsWith(".bat")) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add(codex);
        cmd.add("mcp");
        cmd.add("add");
        cmd.add(SERVER_NAME);
        cmd.add("--url");
        cmd.add(mcpUrl(port));

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output;
        try (var in = p.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("codex mcp add timed out after " + PROCESS_TIMEOUT_SECONDS + "s");
        }
        if (p.exitValue() != 0) {
            throw new IOException("codex mcp add failed (exit " + p.exitValue() + "):\n" + output.trim());
        }
        return codex;
    }

    /** 在 PATH 和常见安装目录里找 codex，找不到返回 null（GUI 进程的 PATH 常缺 ~/.local/bin）。 */
    public static String findCodex() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] names = windows
                ? new String[]{"codex.exe", "codex.cmd", "codex.bat"}
                : new String[]{"codex"};
        List<Path> dirs = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String d : pathEnv.split(java.io.File.pathSeparator)) {
                if (!d.isBlank()) dirs.add(Path.of(d));
            }
        }
        String home = System.getProperty("user.home");
        dirs.add(Path.of(home, ".local", "bin"));
        dirs.add(Path.of(home, ".codex", "bin"));
        if (!windows) {
            dirs.add(Path.of("/usr/local/bin"));
            dirs.add(Path.of("/opt/homebrew/bin"));
        }
        for (Path dir : dirs) {
            for (String name : names) {
                Path candidate = dir.resolve(name);
                if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }
}
