package com.orion;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TerminalEmulator {

    private final StyleClassedTextArea terminal;
    private final String workingDirectory;

    private PtyProcess ptyProcess;
    private OutputStream shellWriter;

    public TerminalEmulator(StyleClassedTextArea terminal, String workingDirectory) {
        this.terminal = terminal;
        this.workingDirectory = workingDirectory;

        setupTerminal();
        startShell();
        setupInputHandling();
    }

    /* -------------------- UI SETUP -------------------- */
    private void setupTerminal() {
        terminal.setEditable(false);
        terminal.setWrapText(true);
        terminal.setStyle(
            "-fx-font-family: Consolas; " +
            "-fx-font-size: 14px; " +
            "-fx-background-color: #16171D; " +
            "-fx-fill: white; " +
            "-fx-text-fill: white;"
        );
        terminal.requestFocus();
    }

    /* -------------------- INPUT HANDLING -------------------- */
    private void setupInputHandling() {
        // Use scene to capture keyboard when terminal is non-editable
        terminal.setOnKeyTyped(e -> {
            String character = e.getCharacter();
            if (shellWriter != null && !character.isEmpty() && character.charAt(0) >= 32) {
                try {
                    shellWriter.write(character.getBytes(StandardCharsets.UTF_8));
                    shellWriter.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        terminal.setOnKeyPressed(e -> {
            if (shellWriter == null) return;
            try {
                switch (e.getCode()) {
                    case ENTER:
                        shellWriter.write("\r".getBytes(StandardCharsets.UTF_8));
                        shellWriter.flush();
                        break;
                    case BACK_SPACE:
                        shellWriter.write(8);
                        shellWriter.flush();
                        break;
                    case TAB:
                        shellWriter.write("\t".getBytes(StandardCharsets.UTF_8));
                        shellWriter.flush();
                        break;
                    case UP:
                        shellWriter.write("\u001b[A".getBytes(StandardCharsets.UTF_8));
                        shellWriter.flush();
                        break;
                    case DOWN:
                        shellWriter.write("\u001b[B".getBytes(StandardCharsets.UTF_8));
                        shellWriter.flush();
                        break;
                    case RIGHT:
                        shellWriter.write("\u001b[C".getBytes(StandardCharsets.UTF_8));
                        shellWriter.flush();
                        break;
                    case LEFT:
                        shellWriter.write("\u001b[D".getBytes(StandardCharsets.UTF_8));
                        shellWriter.flush();
                        break;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    /* -------------------- SHELL PROCESS -------------------- */
    private void startShell() {
        new Thread(() -> {
            try {
                String[] cmd;
                Map<String, String> env = new HashMap<>(System.getenv());

                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    cmd = new String[]{"cmd.exe"};
                    env.put("TERM", "xterm");
                } else {
                    cmd = new String[]{"/bin/bash", "-i"};
                    env.put("TERM", "xterm-256color");
                }

                PtyProcessBuilder builder = new PtyProcessBuilder(cmd)
                        .setDirectory(workingDirectory)
                        .setEnvironment(env)
                        .setInitialColumns(80)
                        .setInitialRows(24);

                ptyProcess = builder.start();
                shellWriter = ptyProcess.getOutputStream();

                readStream(ptyProcess.getInputStream());

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> terminal.appendText("Shell error: " + e.getMessage() + "\n"));
            }
        }, "Shell-Starter").start();
    }

    private void readStream(InputStream stream) {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = stream.read(buffer)) > 0) {
                    String text = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    Platform.runLater(() -> processOutput(text));
                }
            } catch (IOException ignored) {}
        }, "Shell-Reader").start();
    }

    private void processOutput(String output) {
        for (int i = 0; i < output.length(); i++) {
            char c = output.charAt(i);

            if (c == '\b') { // Backspace
                int len = terminal.getLength();
                if (len > 0) terminal.deleteText(len - 1, len);
            } else if (c == '\r') { // Carriage return
                int lastNewline = terminal.getText().lastIndexOf('\n');
                terminal.moveTo(lastNewline + 1);
            } else if (c == '\u001b') { // ANSI escape sequence
                while (i < output.length() - 1 && !Character.isLetter(output.charAt(i + 1))) i++;
                if (i < output.length() - 1) i++;
            } else { // Regular character
                terminal.appendText(String.valueOf(c));
            }
        }
        terminal.moveTo(terminal.getLength());
    }

    public void clear() {
        try {
            if (shellWriter != null) {
                String clearCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "cls\n" : "clear\n";
                shellWriter.write(clearCmd.getBytes(StandardCharsets.UTF_8));
                shellWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            ptyProcess.destroy();
        }
    }

    public void resize(int cols, int rows) {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            try {
                ptyProcess.setWinSize(new WinSize(cols, rows));
            } catch (Exception e) {
                System.err.println("Failed to resize PTY: " + e.getMessage());
            }
        }
    }
}
