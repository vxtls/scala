package javax.swing;

import java.awt.Container;
import java.awt.Graphics;
import java.awt.event.ComponentListener;
import javax.swing.border.Border;

public class JComponent extends Container {
    public JComponent() {}

    protected class AccessibleJComponent {
        protected AccessibleJComponent() {}
    }

    protected void paintComponent(Graphics g) {}
    protected void paintBorder(Graphics g) {}
    protected void paintChildren(Graphics g) {}

    public void setAlignmentX(float alignmentX) {}
    public void setAlignmentY(float alignmentY) {}
    public Border getBorder() { return null; }
    public void setBorder(Border border) {}
    public void setOpaque(boolean opaque) {}
    public boolean isOpaque() { return false; }
    public String getToolTipText() { return null; }
    public void setToolTipText(String text) {}
    public InputVerifier getInputVerifier() { return null; }
    public void setInputVerifier(InputVerifier verifier) {}
    public void putClientProperty(Object key, Object value) {}
    public Object getClientProperty(Object key) { return null; }
    public void addComponentListener(ComponentListener listener) {}
}
