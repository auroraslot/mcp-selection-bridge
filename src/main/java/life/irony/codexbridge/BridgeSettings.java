package life.irony.codexbridge;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/** 应用级持久化设置：是否启用、监听端口。改动需重启 IDE 生效。 */
@Service
@State(name = "SelectionBridgeForCodex", storages = @Storage("selectionBridgeForCodex.xml"))
public final class BridgeSettings implements PersistentStateComponent<BridgeSettings.State> {

    public static class State {
        public boolean enabled = true;
        public int port = 63450;
    }

    private State state = new State();

    public static BridgeSettings getInstance() {
        return ApplicationManager.getApplication().getService(BridgeSettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(State state) {
        this.state = state;
    }
}
