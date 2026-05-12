package javax.swing;

public interface ComboBoxModel extends ListModel {
    void setSelectedItem(Object item);
    Object getSelectedItem();
}
