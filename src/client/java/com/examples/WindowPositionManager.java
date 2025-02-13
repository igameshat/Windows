package com.examples;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import java.awt.Rectangle;
import java.awt.Point;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class WindowPositionManager {
    private static final Map<Long, Rectangle> windowBounds = new ConcurrentHashMap<>();
    private static final int TASKBAR_HEIGHT = 40;
    private static final int DEFAULT_CASCADE_OFFSET = 30;

    public static void registerWindow(long window) {
        // Get monitor information first
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(monitor);
        if (vidMode == null) return;

        int screenWidth = vidMode.width();
        int screenHeight = vidMode.height();

        try (MemoryStack stack = stackPush()) {
            // Get window size first
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            glfwGetWindowSize(window, width, height);

            // Calculate cascade position
            int cascade = windowBounds.size() * DEFAULT_CASCADE_OFFSET;
            int newX = Math.min(cascade, screenWidth - width.get(0));
            int newY = Math.min(cascade, screenHeight - TASKBAR_HEIGHT - height.get(0));

            // Ensure window is on screen
            newX = Math.max(0, newX);
            newY = Math.max(0, newY);

            // Set window position
            glfwSetWindowPos(window, newX, newY);

            // Store bounds
            Rectangle bounds = new Rectangle(newX, newY, width.get(0), height.get(0));
            windowBounds.put(window, bounds);
        }
    }

    public static void updateWindowBounds(long window, int x, int y, int width, int height) {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(monitor);
        if (vidMode == null) return;

        int screenWidth = vidMode.width();
        int screenHeight = vidMode.height() - TASKBAR_HEIGHT;

        // Ensure window stays within screen bounds
        int newX = Math.max(0, Math.min(x, screenWidth - width));
        int newY = Math.max(0, Math.min(y, screenHeight - height));

        Rectangle bounds = windowBounds.get(window);
        if (bounds != null) {
            bounds.setBounds(newX, newY, width, height);
        } else {
            bounds = new Rectangle(newX, newY, width, height);
            windowBounds.put(window, bounds);
        }
    }

    public static void unregisterWindow(long window) {
        windowBounds.remove(window);
    }

    public static Rectangle getWindowBounds(long window) {
        return windowBounds.get(window);
    }
}