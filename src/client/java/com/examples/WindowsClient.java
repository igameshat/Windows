package com.examples;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Icons;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class WindowsClient implements ClientModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("WindowsClient");
    private static final List<String> windowList = new ArrayList<>();
    private static final int MAX_WINDOWS = 10000;
    private static final Map<String, Long> activeWindows = new ConcurrentHashMap<>();
    private static final Map<String, Thread> windowThreads = new ConcurrentHashMap<>();
    private static volatile boolean shouldClose = false;

    static {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
    }

    private static class WindowCallbacks {
        private static final GLFWWindowCloseCallback closeCallback = new GLFWWindowCloseCallback() {
            @Override
            public void invoke(long window) {
                String windowName = windowList.stream()
                        .filter(name -> activeWindows.get(name) == window)
                        .findFirst()
                        .orElse(null);

                if (windowName != null) {
                    MinecraftClient.getInstance().execute(() -> closeSystemWindow(windowName));
                }
            }
        };
    }

    private static final SuggestionProvider<FabricClientCommandSource> WINDOW_SUGGESTION_PROVIDER =
            (context, builder) -> net.minecraft.command.CommandSource.suggestMatching(
                    windowList.stream()
                            .filter(s -> s.toLowerCase().startsWith(builder.getRemaining().toLowerCase())),
                    builder
            );

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("windows")
                .then(ClientCommandManager.literal("list")
                        .executes(context -> {
                            if (windowList.isEmpty()) {
                                context.getSource().sendFeedback(Text.literal("No windows open."));
                            } else {
                                context.getSource().sendFeedback(Text.literal("Open windows: \n" + String.join(", ", windowList)));
                            }
                            return 1;
                        }))
                .then(ClientCommandManager.literal("open")
                        .executes(context -> {
                            if (windowList.size() >= MAX_WINDOWS) {
                                context.getSource().sendError(Text.literal("Window limit reached (max " + MAX_WINDOWS + ")."));
                                return 0;
                            }
                            String windowName = generateUniqueWindowName();
                            openSystemWindow(windowName);
                            context.getSource().sendFeedback(Text.literal("Opened window: " + windowName));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("window_name", StringArgumentType.string())
                                .executes(context -> {
                                    if (windowList.size() >= MAX_WINDOWS) {
                                        context.getSource().sendError(Text.literal("Window limit reached (max " + MAX_WINDOWS + ")."));
                                        return 0;
                                    }
                                    String windowName = StringArgumentType.getString(context, "window_name");
                                    if (windowList.contains(windowName)) {
                                        context.getSource().sendError(Text.literal("A window with that name already exists!"));
                                        return 0;
                                    }
                                    openSystemWindow(windowName);
                                    context.getSource().sendFeedback(Text.literal("Opened window: " + windowName));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("close")
                        .executes(context -> {
                            if (!windowList.isEmpty()) {
                                String lastWindow = windowList.get(windowList.size() - 1);
                                closeSystemWindow(lastWindow);
                                context.getSource().sendFeedback(Text.literal("Closed window: " + lastWindow));
                            } else {
                                context.getSource().sendError(Text.literal("No windows to close."));
                            }
                            return 1;
                        })
                        .then(ClientCommandManager.argument("window_name", StringArgumentType.string())
                                .suggests(WINDOW_SUGGESTION_PROVIDER)
                                .executes(context -> {
                                    String windowName = StringArgumentType.getString(context, "window_name");
                                    if (windowList.contains(windowName)) {
                                        closeSystemWindow(windowName);
                                        context.getSource().sendFeedback(Text.literal("Closed window: " + windowName));
                                        return 1;
                                    } else {
                                        context.getSource().sendError(Text.literal("Window '" + windowName + "' not found."));
                                        return 0;
                                    }
                                }))));
    }

    private static void openSystemWindow(String windowName) {
        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);

        // Create the window
        long window = glfwCreateWindow(800, 600, windowName, 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create GLFW window");
        }


        // Center the window
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        }

        // Make the window visible
        glfwShowWindow(window);

        // Set up the close callback
        glfwSetWindowCloseCallback(window, WindowCallbacks.closeCallback);

        // Store the window
        windowList.add(windowName);
        activeWindows.put(windowName, window);

        // Create a separate thread for the window's render loop
        Thread renderThread = new Thread(() -> {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

            while (!glfwWindowShouldClose(window)) {
                glClear(GL_COLOR_BUFFER_BIT);
                glfwSwapBuffers(window);
                glfwPollEvents();
            }

            // Clean up when the loop ends
            glfwMakeContextCurrent(0);
            GL.setCapabilities(null);

            MinecraftClient.getInstance().execute(() -> {
                glfwDestroyWindow(window);
                windowList.remove(windowName);
                activeWindows.remove(windowName);
                windowThreads.remove(windowName);
                LOGGER.debug("Window closed completely: {}", windowName);
            });
        }, "Window-" + windowName);

        windowThreads.put(windowName, renderThread);
        renderThread.start();
    }

    private static void closeSystemWindow(String windowName) {
        Long windowHandle = activeWindows.get(windowName);
        if (windowHandle != null) {
            glfwSetWindowShouldClose(windowHandle, true);
            LOGGER.debug("Requesting close for window: {}", windowName);

            Thread renderThread = windowThreads.get(windowName);
            if (renderThread != null) {
                try {
                    renderThread.join(100); // Wait briefly for the thread to close
                } catch (InterruptedException e) {
                    LOGGER.error("Interrupted while waiting for window thread to close", e);
                }
            }
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
        ClientCommandRegistrationCallback.EVENT.register(WindowsClient::registerCommands);
    }
}
