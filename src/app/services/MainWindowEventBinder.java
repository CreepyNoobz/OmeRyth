package app.services;

import app.utils.KeyBoardListener;
import app.ui.TimelinePanel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

public class MainWindowEventBinder {

    /**
     * Bind common application-level event handlers: global mouse events, wheel handling
     * and window close callback using the provided handlers.
     */
    public void bind(JFrame frame,
                     TimelinePanel timelinePanel,
                     KeyBoardListener keyBoardListener,
                     ActionHistoryService actionHistoryService,
                     Consumer<MouseWheelEvent> wheelHandler,
                     Runnable onWindowClosing) {

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseEvent) {
                MouseEvent me = (MouseEvent) event;
                if (me.getID() == MouseEvent.MOUSE_PRESSED && timelinePanel.isEditing()) {
                    Component src = me.getComponent();
                    if (src != timelinePanel && !SwingUtilities.isDescendingFrom(src, timelinePanel)) {
                        timelinePanel.stopTyping();
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseWheelEvent)) return;
            // Ne pas gérer si la fenêtre principale n'est pas la fenêtre active (ex: une boîte de dialogue est ouverte)
            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (activeWindow != frame) return;
            
            MouseWheelEvent mwe = (MouseWheelEvent) event;
            if (mwe.isConsumed()) return;
            Component src = mwe.getComponent();
            if (src == null) return;
            if (!SwingUtilities.isDescendingFrom(src, frame) && src != frame) return;

            // Ne pas intercepter la molette si la souris se trouve sur le panneau d'historique ou un JScrollPane
            if (actionHistoryService != null && actionHistoryService.isHistoryComponent(src)) {
                return;
            }
            if (src instanceof javax.swing.JScrollPane || src instanceof javax.swing.JScrollBar
                    || SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, src) != null) {
                return;
            }

            mwe.consume();
            wheelHandler.accept(mwe);
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            // Ne pas gérer si la fenêtre principale n'est pas la fenêtre active
            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (activeWindow != frame) return false;
            
            Component src = e.getComponent();
            if (src == null) {
                src = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            }
            if (src == null) return false;
            if (!(src == frame || SwingUtilities.isDescendingFrom(src, frame))) {
                return false;
            }

            if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                if (keyBoardListener.dispatchKeyEvent(e)) {
                    return true;
                }
            } else if (e.getID() == java.awt.event.KeyEvent.KEY_RELEASED) {
                keyBoardListener.keyReleased(e);
            }
            return false;
        });

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            // Window closing callback that delegates to the provided Runnable.
            public void windowClosing(java.awt.event.WindowEvent e) {
                onWindowClosing.run();
            }
        });
    }

    public void bind(JFrame frame,
                     TimelinePanel timelinePanel,
                     KeyBoardListener keyBoardListener,
                     Consumer<MouseWheelEvent> wheelHandler,
                     Runnable onWindowClosing) {
        bind(frame, timelinePanel, keyBoardListener, null, wheelHandler, onWindowClosing);
    }

    private boolean isFromMainOrOwnedWindow(JFrame frame, Component src) {
        Window window = (src instanceof Window) ? (Window) src : SwingUtilities.getWindowAncestor(src);
        while (window != null) {
            if (window == frame) {
                return true;
            }
            window = window.getOwner();
        }
        return false;
    }
}
