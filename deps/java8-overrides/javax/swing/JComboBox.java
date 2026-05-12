package javax.swing;

import java.awt.event.ActionListener;

public class JComboBox extends JComponent {
    public JComboBox() {}
    public JComboBox(ComboBoxModel model) {}

    public ComboBoxEditor getEditor() { return null; }
    public void setEditor(ComboBoxEditor editor) {}
    public int getSelectedIndex() { return 0; }
    public void setSelectedIndex(int index) {}
    public Object getSelectedItem() { return null; }
    public void setSelectedItem(Object item) {}
    public void addActionListener(ActionListener listener) {}
    public ListCellRenderer getRenderer() { return null; }
    public void setRenderer(ListCellRenderer renderer) {}
    public boolean isEditable() { return false; }
    public void setEditable(boolean editable) {}
    public Object getPrototypeDisplayValue() { return null; }
    public void setPrototypeDisplayValue(Object value) {}
}
