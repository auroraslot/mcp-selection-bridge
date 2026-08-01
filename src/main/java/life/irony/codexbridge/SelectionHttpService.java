package life.irony.codexbridge;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 应用启动时按设置拉起内置 MCP 服务。 */
public class SelectionHttpService implements AppLifecycleListener {
    private static final Logger LOG = Logger.getInstance(SelectionHttpService.class);
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    @Override
    public void appFrameCreated(List<String> commandLineArgs) {
        if (!STARTED.compareAndSet(false, true)) return;
        BridgeSettings.State settings = BridgeSettings.getInstance().getState();
        if (!settings.enabled) {
            LOG.info("Selection Bridge for Codex is disabled in settings");
            return;
        }
        try {
            McpHttpServer server = new McpHttpServer(settings.port, new IdeSelectionProvider());
            server.start();
            LOG.info("Selection Bridge for Codex listening on 127.0.0.1:" + server.getPort());
        } catch (IOException e) {
            LOG.warn("Selection Bridge for Codex failed to start on port " + settings.port
                    + " (is another IDE instance using it?)", e);
        }
    }
}
