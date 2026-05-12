package javax.swing;

import java.awt.Component;
import java.io.Serializable;

public class DefaultListCellRenderer extends JLabel implements ListCellRenderer, Serializable {
    public DefaultListCellRenderer() {}

    public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        return this;
    }
}
