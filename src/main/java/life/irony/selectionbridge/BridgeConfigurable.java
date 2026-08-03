package life.irony.selectionbridge;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Settings | Tools | MCP Selection Bridge */
public class BridgeConfigurable implements Configurable {
    private static final String DIALOG_TITLE = "MCP Selection Bridge";

    private JCheckBox enabledBox;
    private JTextField portField;
    private JButton codexButton;
    private JButton kimiButton;

    @Override
    public String getDisplayName() {
        return DIALOG_TITLE;
    }

    @Override
    public JComponent createComponent() {
        enabledBox = new JCheckBox("Enable local MCP server");
        portField = new JTextField(8);
        JPanel portRow = new JPanel();
        portRow.setLayout(new BoxLayout(portRow, BoxLayout.X_AXIS));
        portRow.add(new JLabel("Port (127.0.0.1): "));
        portRow.add(portField);
        portRow.add(Box.createHorizontalGlue());

        codexButton = new JButton("Register in Codex");
        codexButton.addActionListener(e -> registerCodexClicked());
        kimiButton = new JButton("Register in Kimi Code");
        kimiButton.addActionListener(e -> registerKimiClicked());
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.add(codexButton);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(kimiButton);
        buttonRow.add(Box.createHorizontalGlue());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{enabledBox, portRow,
                new JLabel("Changes take effect after restarting the IDE."),
                buttonRow,
                new JLabel("Manual alternative — codex:  codex mcp add idea-selection --url http://127.0.0.1:<port>/mcp"),
                new JLabel("Manual alternative — kimi:  run /mcp-config inside kimi, or add the URL to ~/.kimi-code/mcp.json")}) {
            c.setAlignmentX(0f);
            panel.add(c);
            panel.add(Box.createVerticalStrut(8));
        }
        reset();
        return panel;
    }

    // ---------- 一键注册 ----------

    private void registerCodexClicked() {
        Integer port = portFromField();
        if (port == null) return;
        if (CliRegistration.findCodex() == null) {
            // GUI 进程的 PATH 常缺 ~/.local/bin 之类目录：找不到就退化为复制命令
            String cmd = "codex mcp add " + CliRegistration.SERVER_NAME + " --url " + CliRegistration.mcpUrl(port);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cmd), null);
            Messages.showInfoMessage(
                    "codex executable not found. The register command was copied to the clipboard — "
                            + "paste it into any terminal:\n\n" + cmd,
                    DIALOG_TITLE);
            return;
        }
        runAsync(codexButton, port, () -> {
            String codex = CliRegistration.registerCodex(port);
            return "Registered in codex (" + codex + ").\nNew codex sessions can now use "
                    + McpHttpServer.TOOL_NAME + ".";
        });
    }

    private void registerKimiClicked() {
        Integer port = portFromField();
        if (port == null) return;
        runAsync(kimiButton, port, () -> {
            java.nio.file.Path file = CliRegistration.registerKimi(port);
            return "Written to " + file + ".\nNew kimi sessions can now use "
                    + McpHttpServer.TOOL_NAME + ".";
        });
    }

    private interface RegisterAction {
        String run() throws Exception;
    }

    /** 注册动作在池化线程执行（外部进程/文件 IO 不能占 EDT），结果回 EDT 弹窗。 */
    private void runAsync(JButton button, int port, RegisterAction action) {
        button.setEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String message;
            Exception failure = null;
            String note = portNote(port);
            try {
                message = action.run() + note;
            } catch (Exception e) {
                failure = e;
                message = null;
            }
            String finalMessage = message;
            Exception finalFailure = failure;
            SwingUtilities.invokeLater(() -> {
                button.setEnabled(true);
                if (finalFailure != null) {
                    Messages.showErrorDialog(String.valueOf(finalFailure.getMessage()), DIALOG_TITLE);
                } else {
                    Messages.showInfoMessage(finalMessage, DIALOG_TITLE);
                }
            });
        });
    }

    /** 注册用的端口和当前生效设置不一致时提醒（改端口需 Apply + 重启才生效）。 */
    private static String portNote(int registeredPort) {
        BridgeSettings.State s = BridgeSettings.getInstance().getState();
        if (registeredPort == s.port && s.enabled) return "";
        if (!s.enabled) {
            return "\n\nNote: the MCP server is currently disabled in settings.";
        }
        return "\n\nNote: the server currently uses port " + s.port
                + ". Apply the settings and restart the IDE for port " + registeredPort + " to take effect.";
    }

    private Integer portFromField() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port >= 1024 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
        }
        Messages.showErrorDialog("Port must be a number between 1024 and 65535", DIALOG_TITLE);
        return null;
    }

    // ---------- 常规设置读写 ----------

    @Override
    public boolean isModified() {
        BridgeSettings.State s = BridgeSettings.getInstance().getState();
        return enabledBox.isSelected() != s.enabled || !portField.getText().trim().equals(String.valueOf(s.port));
    }

    @Override
    public void apply() throws ConfigurationException {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Port must be a number");
        }
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException("Port must be between 1024 and 65535");
        }
        BridgeSettings.State s = BridgeSettings.getInstance().getState();
        s.enabled = enabledBox.isSelected();
        s.port = port;
    }

    @Override
    public void reset() {
        BridgeSettings.State s = BridgeSettings.getInstance().getState();
        enabledBox.setSelected(s.enabled);
        portField.setText(String.valueOf(s.port));
    }
}
