package com.orion;

import java.io.*;
import java.util.function.Consumer;

public class CodeRunner {
    
    // For interactive mode
    public interface ProcessHandler {
        void onOutput(String output);
        void onExit(int exitCode);
        Process getProcess();
    }

    public static String runCode(String filePath, String code) throws IOException, InterruptedException {
        // Save code to file
        File file = new File(filePath);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(code);
        }

        // Get command based on file extension
        if (filePath.endsWith(".c") || filePath.endsWith(".cpp")) {
            return compileAndRunCppOrC(filePath);
        }

        String[] command = getCommand(filePath);

        // Execute the code
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            output.append("\nProcess exited with code: ").append(exitCode);
        }

        return output.toString();
    }
    
    // Interactive mode - for processes that need input
    public static ProcessHandler runInteractive(String filePath, String code, Consumer<String> outputCallback) throws IOException {
        // Save code to file
        File file = new File(filePath);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(code);
        }

        // Handle C/C++ compilation first
        if (filePath.endsWith(".c") || filePath.endsWith(".cpp")) {
            return compileAndRunCppOrCInteractive(filePath, outputCallback);
        }

        // Get command based on file extension
        String[] command = getCommand(filePath);

        // Execute the code
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Create handler to manage this process
        ProcessHandler handler = new ProcessHandler() {
            private final Process proc = process;
            
            @Override
            public void onOutput(String output) {
                outputCallback.accept(output);
            }
            
            @Override
            public void onExit(int exitCode) {
                outputCallback.accept("\n[Process exited with code: " + exitCode + "]\n");
            }
            
            @Override
            public Process getProcess() {
                return proc;
            }
        };
        
        // Read output in a separate thread
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputCallback.accept(line + "\n");
                }
                
                int exitCode = process.waitFor();
                handler.onExit(exitCode);
            } catch (IOException | InterruptedException e) {
                outputCallback.accept("Error: " + e.getMessage() + "\n");
            }
        });
        outputThread.setDaemon(true);
        outputThread.start();
        
        return handler;
    }

    private static String compileAndRunCppOrC(String filePath) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        
        // Determine compiler
        String compiler = filePath.endsWith(".cpp") ? "g++" : "gcc";
        String outputExe = "output_" + System.currentTimeMillis() + ".exe";
        
        // Step 1: Compile
        ProcessBuilder compileBuilder = new ProcessBuilder(compiler, filePath, "-o", outputExe);
        compileBuilder.redirectErrorStream(true);
        Process compileProcess = compileBuilder.start();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(compileProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int compileExitCode = compileProcess.waitFor();
        if (compileExitCode != 0) {
            output.append("\nCompilation failed with exit code: ").append(compileExitCode);
            return output.toString();
        }
        
        output.append("Compilation successful!\n");
        output.append("=".repeat(30)).append("\n");
        
        // Step 2: Run
        ProcessBuilder runBuilder = new ProcessBuilder(outputExe);
        runBuilder.redirectErrorStream(true);
        Process runProcess = runBuilder.start();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(runProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int runExitCode = runProcess.waitFor();
        if (runExitCode != 0) {
            output.append("\nProgram exited with code: ").append(runExitCode);
        }
        
        return output.toString();
    }
    
    private static ProcessHandler compileAndRunCppOrCInteractive(String filePath, Consumer<String> outputCallback) throws IOException {
        // Determine compiler
        String compiler = filePath.endsWith(".cpp") ? "g++" : "gcc";
        String outputExe = "output_" + System.currentTimeMillis() + ".exe";
        
        // Create a mutable handler that we can update once the executable starts
        final Process[] currentProcess = new Process[1]; // Array to hold mutable reference
        
        ProcessHandler handler = new ProcessHandler() {
            @Override
            public void onOutput(String output) {
                outputCallback.accept(output);
            }
            
            @Override
            public void onExit(int exitCode) {
                outputCallback.accept("\n[Process exited with code: " + exitCode + "]\n");
            }
            
            @Override
            public Process getProcess() {
                return currentProcess[0];
            }
        };
        
        // Step 1: Compile and run in background thread
        Thread compileThread = new Thread(() -> {
            try {
                // Compile
                ProcessBuilder compileBuilder = new ProcessBuilder(compiler, filePath, "-o", outputExe);
                compileBuilder.redirectErrorStream(true);
                Process compileProcess = compileBuilder.start();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(compileProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputCallback.accept(line + "\n");
                    }
                }
                
                int compileExitCode = compileProcess.waitFor();
                if (compileExitCode != 0) {
                    outputCallback.accept("\nCompilation failed with exit code: " + compileExitCode + "\n");
                    return;
                }
                
                outputCallback.accept("Compilation successful!\n");
                outputCallback.accept("=".repeat(30) + "\n");
                
                // Step 2: Run the compiled executable
                ProcessBuilder runBuilder = new ProcessBuilder(outputExe);
                runBuilder.redirectErrorStream(true);
                Process runProcess = runBuilder.start();
                
                // Update the handler to point to the running process
                currentProcess[0] = runProcess;
                
                // Read runtime output in a non-blocking way
                BufferedReader runReader = new BufferedReader(
                        new InputStreamReader(runProcess.getInputStream()));
                String runLine;
                while ((runLine = runReader.readLine()) != null) {
                    outputCallback.accept(runLine + "\n");
                }
                
                int runExitCode = runProcess.waitFor();
                handler.onExit(runExitCode);
                currentProcess[0] = null; // Clear reference after process ends
                
            } catch (IOException | InterruptedException e) {
                outputCallback.accept("Error: " + e.getMessage() + "\n");
            }
        });
        compileThread.setDaemon(true);
        compileThread.start();
        
        return handler;
    }

    private static String[] getCommand(String filePath) {
        if (filePath.endsWith(".py")) {
            return new String[]{"python", filePath};
        } else if (filePath.endsWith(".java")) {
            return new String[]{"java", filePath};
        } else if (filePath.endsWith(".js")) {
            return new String[]{"node", filePath};
        }
        throw new IllegalArgumentException("Unsupported file type: " + filePath);
    }
}
