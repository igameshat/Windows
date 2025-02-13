package com.examples;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.glfw.GLFWVidMode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class WindowsClient implements ClientModInitializer {
    private static final List<String> windowList = new ArrayList<>();
    private static final Map<String, Long> activeWindows = new ConcurrentHashMap<>();
    private static final Map<String, Thread> windowThreads = new ConcurrentHashMap<>();
    private static final int MAX_WINDOWS = 10000;

    private static final KeywordProcessor keywordProcessor = new KeywordProcessor();

    static {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
    }

    static File getWindowsClientConfigDir() {
        File baseConfigDir = FabricLoader.getInstance().getConfigDir().toFile();
        File windowsClientDir = new File(baseConfigDir, "WindowsClient");

        if (!windowsClientDir.exists()) {
            windowsClientDir.mkdirs();
        }

        return windowsClientDir;
    }

    private static void openSystemWindow(String windowName) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);
        glfwWindowHint(GLFW_FOCUSED, GLFW_TRUE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);

        // Increased default width to accommodate all UI elements
        long window = glfwCreateWindow(1024, 600, windowName, 0, 0);  // Width increased from 800 to 1024
        if (window == 0) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Set minimum window size to ensure all UI elements are visible
        glfwSetWindowSizeLimits(window, 800, 400, GLFW_DONT_CARE, GLFW_DONT_CARE);

        // Initialize UI
        UI.initializeWindow(window);

        // Center window on screen in a cascading pattern
        try (MemoryStack stack = stackPush()) {
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                int offset = windowList.size() * 30;  // 30 pixel offset for each window
                int x = (vidmode.width() - 800) / 2 + offset;
                int y = (vidmode.height() - 600) / 2 + offset;

                // Make sure window stays on screen
                x = Math.max(0, Math.min(x, vidmode.width() - 800));
                y = Math.max(0, Math.min(y, vidmode.height() - 600));

                glfwSetWindowPos(window, x, y);
            }
        }

        // Make window visible
        glfwShowWindow(window);

        // Create render thread
        Thread renderThread = new Thread(() -> {
            try {
                glfwMakeContextCurrent(window);
                GL.createCapabilities();

                while (!glfwWindowShouldClose(window)) {
                    try {
                        UI.render(window);
                        glfwPollEvents();
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                // Cleanup
                UI.cleanup(window);
                glfwMakeContextCurrent(0);
                GL.setCapabilities(null);
                glfwDestroyWindow(window);
            } catch (Exception e) {
                System.err.println("Error in render thread: " + e.getMessage());
                e.printStackTrace();
            }
        }, "Window-" + windowName);

        // Store window information
        windowList.add(windowName);
        activeWindows.put(windowName, window);
        windowThreads.put(windowName, renderThread);

        // Start render thread
        renderThread.start();
    }


    private static void cleanupWindow(String windowName) {
        Long windowHandle = activeWindows.get(windowName);
        Thread renderThread = windowThreads.get(windowName);

        if (windowHandle != null) {
            // Tell GLFW to close the window
            glfwSetWindowShouldClose(windowHandle, true);

            // Force a window hide
            glfwHideWindow(windowHandle);

            // Post an empty event to wake up the render thread
            glfwPostEmptyEvent();

            // Unregister from position manager
            WindowPositionManager.unregisterWindow(windowHandle);

            // Wait for render thread
            if (renderThread != null) {
                try {
                    renderThread.join(1000);
                    if (renderThread.isAlive()) {
                        renderThread.interrupt();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Clean up tracking maps
            activeWindows.remove(windowName);
            windowThreads.remove(windowName);
            windowList.remove(windowName);
        }
    }

    private static String generateUniqueWindowName() {
        int counter = 1;
        String name;
        do {
            name = "Window-" + counter++;
        } while (windowList.contains(name));
        return name;
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                  CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("windows")
                .then(ClientCommandManager.literal("open")
                        .executes(context -> {
                            if (windowList.size() >= MAX_WINDOWS) {
                                context.getSource().sendError(Text.of("Window limit reached (max " + MAX_WINDOWS + ")."));
                                return 0;
                            }
                            String windowName = generateUniqueWindowName();
                            openSystemWindow(windowName);
                            context.getSource().sendFeedback(Text.of("Opened window: " + windowName));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("window_name", StringArgumentType.string())
                                .executes(context -> {
                                    if (windowList.size() >= MAX_WINDOWS) {
                                        context.getSource().sendError(Text.of("Window limit reached (max " + MAX_WINDOWS + ")."));
                                        return 0;
                                    }
                                    String windowName = StringArgumentType.getString(context, "window_name");
                                    if (windowList.contains(windowName)) {
                                        context.getSource().sendError(Text.of("A window with that name already exists!"));
                                        return 0;
                                    }
                                    openSystemWindow(windowName);
                                    context.getSource().sendFeedback(Text.of("Opened window: " + windowName));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("close")
                        .executes(context -> {
                            if (!windowList.isEmpty()) {
                                String lastWindow = windowList.get(windowList.size() - 1);
                                cleanupWindow(lastWindow);
                                context.getSource().sendFeedback(Text.of("Closed window: " + lastWindow));
                            } else {
                                context.getSource().sendError(Text.of("No windows to close."));
                            }
                            return 1;
                        })
                        .then(ClientCommandManager.argument("window_name", StringArgumentType.string())
                                .executes(context -> {
                                    String windowName = StringArgumentType.getString(context, "window_name");
                                    if (windowList.contains(windowName)) {
                                        cleanupWindow(windowName);
                                        context.getSource().sendFeedback(Text.of("Closed window: " + windowName));
                                        return 1;
                                    } else {
                                        context.getSource().sendError(Text.of("Window '" + windowName + "' not found."));
                                        return 0;
                                    }
                                })))
                .then(ClientCommandManager.literal("list")
                        .executes(context -> {
                            if (windowList.isEmpty()) {
                                context.getSource().sendFeedback(Text.of("No windows open."));
                            } else {
                                context.getSource().sendFeedback(Text.of("Open windows: \n" + String.join(", ", windowList)));
                            }
                            return 1;
                        }))
                .then(ClientCommandManager.literal("keywords")
                        .executes(context -> {
                            String[] keywords = KeywordProcessor.getAvailableKeywords();
                            context.getSource().sendFeedback(Text.of("Available keywords:\n" + String.join("\n", keywords)));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("debug")
                        .executes(context -> {
                            boolean newState = !ErrorHandler.isDebugMode();
                            ErrorHandler.setDebugMode(newState);
                            context.getSource().sendFeedback(Text.of("Debug mode " + (newState ? "enabled" : "disabled")));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("errors")
                        .executes(context -> {
                            List<ErrorHandler.ErrorRecord> errors = ErrorHandler.getRecentErrors();
                            if (errors.isEmpty()) {
                                context.getSource().sendFeedback(Text.of("No recent errors."));
                            } else {
                                StringBuilder sb = new StringBuilder("Recent errors:\n");
                                for (ErrorHandler.ErrorRecord error : errors) {
                                    sb.append(error.timestamp).append(": ").append(error.message).append("\n");
                                }
                                context.getSource().sendFeedback(Text.of(sb.toString()));
                            }
                            return 1;
                        }))
        );
    }
}