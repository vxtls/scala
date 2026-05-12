package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;

public class JList extends JComponent implements Scrollable {
    public JList() {}
    public JList(ListModel model) {}

    public ListModel getModel() { return null; }
    public void setModel(ListModel model) {}
    public ListSelectionModel getSelectionModel() { return null; }
    public int[] getSelectedIndices() { return null; }
    public void removeSelectionInterval(int index0, int index1) {}
    public void addSelectionInterval(int index0, int index1) {}
    public Object[] getSelectedValues() { return null; }
    public ListCellRenderer getCellRenderer() { return null; }
    public void setCellRenderer(ListCellRenderer renderer) {}
    public int getFixedCellWidth() { return 0; }
    public void setFixedCellWidth(int width) {}
    public int getFixedCellHeight() { return 0; }
    public void setFixedCellHeight(int height) {}
    public Object getPrototypeCellValue() { return null; }
    public void setPrototypeCellValue(Object value) {}
    public int getVisibleRowCount() { return 0; }
    public void setVisibleRowCount(int visibleRowCount) {}
    public void ensureIndexIsVisible(int index) {}
    public Color getSelectionForeground() { return null; }
    public void setSelectionForeground(Color foreground) {}
    public Color getSelectionBackground() { return null; }
    public void setSelectionBackground(Color background) {}
    public void setSelectedIndices(int[] indices) {}

    public Dimension getPreferredScrollableViewportSize() { return null; }
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 0; }
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 0; }
    public boolean getScrollableTracksViewportWidth() { return false; }
    public boolean getScrollableTracksViewportHeight() { return false; }
}
