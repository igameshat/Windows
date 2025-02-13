package com.examples;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("WindowsClient");
    private static final Path LOG_DIRECTORY;
    private static final int MAX_ERROR_HISTORY = 100;
    private static final ConcurrentLinkedQueue<ErrorRecord> errorHistory = new ConcurrentLinkedQueue<>();
    private static boolean debugMode = false;

    static String reportFileName = "error_report_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt";

    static {
        // Initialize the log directory in the game folder
        Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();
        LOG_DIRECTORY = gameDir.resolve("logs").resolve("windows_client");
        try {
            Files.createDirectories(LOG_DIRECTORY);
        } catch (IOException e) {
            LOGGER.error("Failed to create log directory", e);
        }
    }

    public static class ErrorRecord {
        public final String message;
        public final String stackTrace;
        public final LocalDateTime timestamp;
        public final String contextInfo;

        public ErrorRecord(String message, Throwable error, String contextInfo) {
            this.message = message;
            this.stackTrace = getStackTraceAsString(error);
            this.timestamp = LocalDateTime.now();
            this.contextInfo = contextInfo;
        }

        private static String getStackTraceAsString(Throwable throwable) {
            if (throwable == null) return "No stack trace available";
            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                throwable.printStackTrace(pw);
            }
            return sw.toString();
        }
    }

    public static void handleError(String message, Throwable error, String context) {
        // Create an error record
        ErrorRecord record = new ErrorRecord(message, error, context);

        // Add to error history with a size limit
        errorHistory.add(record);
        while (errorHistory.size() > MAX_ERROR_HISTORY) {
            errorHistory.poll();
        }

        // Log the error
        LOGGER.error("[{}] {}: {}", context, message, error != null ? error.getMessage() : "No error message");

        // Log the full stack trace in debug mode
        if (debugMode && error != null) {
            LOGGER.error("Stack trace:", error);
        }

        // Notify the player in-game if possible
        notifyPlayer("[Error] " + message + " (Check logs for details)");

        // Write the error to the log file
        logErrorToFile(record);
    }

    public static void debug(String message) {
        if (debugMode) {
            LOGGER.debug(message);
            logToFile("debug.log", "[DEBUG] " + message);
        }
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        LOGGER.info("Debug mode {}", enabled ? "enabled" : "disabled");
    }

    private static void logErrorToFile(ErrorRecord record) {
        String logEntry = String.format("[%s] %s%nContext: %s%n%s%n%n",
                record.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                record.message,
                record.contextInfo,
                record.stackTrace);

        logToFile("errors.log", logEntry);
    }

    private static synchronized void logToFile(String fileName, String content) {
        Path logFile = LOG_DIRECTORY.resolve(fileName);
        try {
            Files.write(logFile,
                    content.getBytes(),
                    Files.exists(logFile) ?
                            java.nio.file.StandardOpenOption.APPEND :
                            java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException e) {
            LOGGER.error("Failed to write to log file: {}", fileName, e);
        }
    }

    public static List<ErrorRecord> getRecentErrors() {
        return new ArrayList<>(errorHistory);
    }

    public static void generateErrorReport() {
        String fileName = "error_report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt";

        try {
            StringBuilder report = new StringBuilder();
            report.append("Windows Client Error Report\n");
            report.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

            report.append("System Information:\n");
            report.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
            report.append("OS: ").append(System.getProperty("os.name")).append(" ")
                    .append(System.getProperty("os.version")).append("\n");
            report.append("Memory: ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append("MB max\n\n");

            report.append("Recent Errors:\n");
            for (ErrorRecord error : errorHistory) {
                report.append("\n[").append(error.timestamp).append("] ").append(error.message).append("\n");
                report.append("Context: ").append(error.contextInfo).append("\n");
                report.append("Stack Trace:\n").append(error.stackTrace).append("\n");
            }

            logToFile(fileName, report.toString());
            notifyPlayer("Error report generated: " + fileName);

            LOGGER.info("Error report generated: {}", fileName);
        } catch (Exception e) {
            LOGGER.error("Failed to generate error report", e);
        }
    }

    private static void notifyPlayer(String message) {
        if (message == null || message.isEmpty()) {
            return; // Don't send empty or null messages
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.execute(() -> {
                String reportFileName = "error_report_" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt";

                Text messageError = Text.literal("Error report generated: " + reportFileName)
                        .formatted(Formatting.GREEN);
                client.player.sendMessage(messageError, true);
            });
        }
    }

    public static boolean isDebugMode() {
        return debugMode;
    }
}
