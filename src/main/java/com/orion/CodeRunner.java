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

    private static String[] getCommand(String filePath) {
        if (filePath.endsWith(".py")) {
            return new String[]{"python", filePath};
        } else if (filePath.endsWith(".java")) {
            // For Java, we need to compile first then run
            String className = new File(filePath).getName().replace(".java", "");
            return new String[]{"java", filePath};
        } else if (filePath.endsWith(".js")) {
            return new String[]{"node", filePath};
        } else if (filePath.endsWith(".c")) {
            return new String[]{"gcc", filePath, "-o", "output.exe", "&&", "output.exe"};
        } else if (filePath.endsWith(".cpp")) {
            return new String[]{"g++", filePath, "-o", "output.exe", "&&", "output.exe"};
        }
        throw new IllegalArgumentException("Unsupported file type: " + filePath);
    }
}
