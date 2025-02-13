package com.examples;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.awt.*;
import java.io.*;
import java.util.*;

import static com.examples.WindowsClient.getWindowsClientConfigDir;

public class ColorConfig {
    private static final File CONFIG_FILE = new File(
            getWindowsClientConfigDir(),
            "colors.json"
    );
    private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(Color.class, new ColorAdapter())
            .setPrettyPrinting()
    .create();
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final int DEFAULT_BUBBLE_WIDTH = 200;
    private static final int DEFAULT_BUBBLE_HEIGHT = 100;

    public Map<String, String> savedColors = new HashMap<>();  // name -> hex color
    private final Map<String, Rectangle> colorBubbleBounds = new HashMap<>(); // name -> bubble bounds
    private final UIPositioningSystem positioningSystem;

    public ColorConfig() {
        positioningSystem = new UIPositioningSystem(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
    }

    static boolean isValidHexColor(String hex) {
        if (hex == null) {
            return false;
        }
        hex = hex.replace("#", "");
        return hex.matches("[0-9A-Fa-f]{6}");
    }

    public static ColorConfig load() {
        ColorConfig config = null;
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ColorConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (config == null) {
            config = new ColorConfig();
        }

        // Initialize positioning for existing colors
        for (String colorName : config.savedColors.keySet()) {
            config.initializeBubblePosition(colorName);
        }

        return config;
    }

    private void initializeBubblePosition(String colorName) {
        Point position = positioningSystem.calculateBubblePosition(
                colorName,
                DEFAULT_BUBBLE_WIDTH,
                DEFAULT_BUBBLE_HEIGHT
        );
        Rectangle bounds = new Rectangle(
                position.x,
                position.y,
                DEFAULT_BUBBLE_WIDTH,
                DEFAULT_BUBBLE_HEIGHT
        );
        colorBubbleBounds.put(colorName, bounds);
        positioningSystem.addBubble(colorName, bounds);
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addColor(String name, String hexColor) {
        if (name == null || name.trim().isEmpty()) {
            System.err.println("Invalid color name: " + name);
            return;
        }
        if (!isValidHexColor(hexColor)) {
            System.err.println("Invalid hex color: " + hexColor);
            return;
        }

        savedColors.put(name, hexColor);
        initializeBubblePosition(name);
        save();
    }

    public void removeColor(String name) {
        if (!savedColors.containsKey(name)) {
            System.err.println("Color name not found: " + name);
            return;
        }

        Rectangle bounds = colorBubbleBounds.get(name);
        if (bounds != null) {
            positioningSystem.removeBubble(name, bounds);
            colorBubbleBounds.remove(name);
        }

        savedColors.remove(name);
        save();
    }

    public void handleWindowResize(int newWidth, int newHeight) {
        positioningSystem.updateWindowSize(newWidth, newHeight);
        // Update stored bounds after repositioning
        for (Map.Entry<String, Rectangle> entry : colorBubbleBounds.entrySet()) {
            Point newPosition = positioningSystem.calculateBubblePosition(
                    entry.getKey(),
                    entry.getValue().width,
                    entry.getValue().height
            );
            entry.getValue().setLocation(newPosition);
        }
    }

    public void updateBubbleSize(String colorName, int newWidth, int newHeight) {
        Rectangle oldBounds = colorBubbleBounds.get(colorName);
        if (oldBounds != null) {
            positioningSystem.updateBubbleSize(colorName, oldBounds, newWidth, newHeight);
            Point newPosition = positioningSystem.calculateBubblePosition(colorName, newWidth, newHeight);
            Rectangle newBounds = new Rectangle(newPosition.x, newPosition.y, newWidth, newHeight);
            colorBubbleBounds.put(colorName, newBounds);
        }
    }

    public Rectangle getBubbleBounds(String colorName) {
        return colorBubbleBounds.get(colorName);
    }

    public void setPreferredBubblePosition(String colorName, Point position) {
        positioningSystem.setPreferredPosition(colorName, position);
        Rectangle bounds = colorBubbleBounds.get(colorName);
        if (bounds != null) {
            bounds.setLocation(position);
        }
    }

    public Map<String, Rectangle> getAllBubbleBounds() {
        return new HashMap<>(colorBubbleBounds);
    }
}