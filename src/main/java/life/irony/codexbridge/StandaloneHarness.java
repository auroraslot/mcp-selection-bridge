package life.irony.codexbridge;

import java.util.Arrays;
import java.util.List;

/**
 * 脱离 IDE 的协议测试入口：用假选区数据把 McpHttpServer 跑起来，
 * 供 curl / codex CLI 做真实客户端回归，避免反复重启 IDEA。
 * 用法：java ... life.irony.codexbridge.StandaloneHarness [port]
 */
public final class StandaloneHarness {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 63451;
        SelectionProvider fake = () -> {
            List<EditorSelection> list = Arrays.asList(
                    new EditorSelection("demo-project", "/tmp/demo", "/tmp/demo/src/Main.java",
                            10, 12, true,
                            "public static void main(String[] args) {\n    System.out.println(\"hello\");\n}"),
                    new EditorSelection("other-project", "/tmp/other", "/tmp/other/README.md",
                            3, 3, false, null));
            return list;
        };
        McpHttpServer server = new McpHttpServer(port, fake);
        server.start();
        System.out.println("harness listening on 127.0.0.1:" + port);
        Thread.currentThread().join();
    }
}
