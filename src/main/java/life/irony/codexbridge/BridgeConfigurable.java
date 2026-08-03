package life.irony.codexbridge;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Settings | Tools | Selection Bridge for Codex & Kimi */
public class BridgeConfigurable implements Configurable {
    private JCheckBox enabledBox;
    private JTextField portField;

    @Override
    public String getDisplayName() {
        return "Selection Bridge for Codex & Kimi";
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

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{enabledBox, portRow,
                new JLabel("Changes take effect after restarting the IDE."),
                new JLabel("Register in codex:  codex mcp add idea-selection --url http://127.0.0.1:<port>/mcp"),
                new JLabel("Register in kimi:  run /mcp-config inside kimi, or add the URL to ~/.kimi-code/mcp.json")}) {
            c.setAlignmentX(0f);
            panel.add(c);
            panel.add(Box.createVerticalStrut(8));
        }
        reset();
        return panel;
    }

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
