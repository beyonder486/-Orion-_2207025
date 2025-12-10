package com.orion;

import java.io.*;

public class CodeRunner {

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

    private static String compileAndRunCppOrC(String filePath) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        
        // Determine compiler
        String compiler = filePath.endsWith(".cpp") ? "g++" : "gcc";
        String outputExe = "output.exe";
        
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
