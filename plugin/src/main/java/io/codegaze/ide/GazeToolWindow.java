package io.codegaze.ide;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;

public final class GazeToolWindow implements ToolWindowFactory {
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
        GazeService service = project.getService(GazeService.class);
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBorder(JBUI.Borders.empty(16));
        JLabel heading = new JLabel("CodeGaze"); heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22)); panel.add(heading);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Share the active editor in VR."));
        panel.add(new JLabel("Eye gaze / head direction / simulator"));
        panel.add(Box.createVerticalStrut(16));
        JLabel status = new JLabel(service.running() ? "Sharing on " + service.url() : "Sharing is off"); panel.add(status);
        JButton start = new JButton("Start sharing"); JButton open = new JButton("Open viewer & recorder"); JButton stop = new JButton("Stop sharing");
        panel.add(start); panel.add(open); panel.add(stop); panel.add(Box.createVerticalStrut(16));
        panel.add(new JLabel("Native client server")); JTextField url = new JTextField(service.url()); url.setEditable(false); url.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); panel.add(url);
        panel.add(new JLabel("Pairing key (keep private)")); JTextField key = new JTextField(service.token()); key.setEditable(false); key.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); panel.add(key);
        panel.add(Box.createVerticalStrut(16));
        JTextArea help = new JTextArea("1. Open a source file and start sharing.\n2. Open the viewer and start a recording.\n3. Use WebXR or the native OpenXR client.\n4. Stop recording and download the ZIP.\n\nThe shared virtual monitor shows the active\neditor, including its gutter and scrolling.\nKeep the IDE window visible and unminimized.\n\nHead direction is a coarse estimate of\nattention, not an eye measurement.\n\nRecordings remain on this computer.\nOnly the active editor is shared; other IDE\npanels and popups are not mapped in v0.1.");
        help.setEditable(false); help.setOpaque(false); help.setLineWrap(true); help.setWrapStyleWord(true); panel.add(help);
        start.addActionListener(e -> { try { service.start(); status.setText("Sharing on " + service.url()); } catch (Exception failure) { status.setText("Cannot start: " + failure.getMessage()); } });
        open.addActionListener(e -> { try { service.start(); BrowserUtil.browse(service.viewerUrl()); status.setText("Sharing on " + service.url()); } catch (Exception failure) { status.setText("Cannot open: " + failure.getMessage()); } });
        stop.addActionListener(e -> { service.stop(); status.setText("Sharing is off; recording stopped"); });
        window.getContentManager().addContent(ContentFactory.getInstance().createContent(new JBScrollPane(panel), "", false));
    }
}
