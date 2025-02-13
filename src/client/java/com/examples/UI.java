package com.examples;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.examples.WindowsClient.getWindowsClientConfigDir;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class UI {
    // State Maps
    private static Map<Long, UIState> windowStates = new ConcurrentHashMap<>();
    private static final Map<Long, List<TabInfo>> windowTabs = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> activeTabIndices = new ConcurrentHashMap<>();
    private static final Map<Long, StringBuilder> inputBuffers = new ConcurrentHashMap<>();
    private static final Map<Long, Float> scrollOffsets = new ConcurrentHashMap<>();
    private static final Map<Long, DragState> dragStates = new ConcurrentHashMap<>();
    private static final Map<Long, List<String>> messageHistory = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> historyIndices = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> cursorPositions = new ConcurrentHashMap<>();
    private static final Map<Long, TextBubble> editingBubbles = new ConcurrentHashMap<>();




    // Constants
    private static final float TAB_HEIGHT = 40.0f;
    private static final float TAB_PADDING = 30.0f;
    private static final float TEXT_BOX_X = 10.0f;
    private static final float TEXT_BOX_WIDTH = 600.0f;
    private static final float TEXT_BOX_HEIGHT = 60.0f;
    private static final float BUTTON_WIDTH = 170.0f;
    private static final float BUTTON_HEIGHT = TEXT_BOX_HEIGHT;
    private static final float SCROLL_SPEED = 30.0f;
    private static final float CONTENT_BOTTOM_MARGIN = 80.0f;
    private static final float BUBBLE_SPACING = 40.0f;  // bubblespacing
    private static final int MAX_INPUT_LENGTH = 1024;
    private static final float DEFAULT_CORNER_RADIUS = 8.0f;

    private static final Map<Long, ContextMenu> activeContextMenus = new ConcurrentHashMap<>();
    private static final float MENU_ITEM_HEIGHT = 25.0f;
    private static final float MENU_WIDTH = 120.0f;

    private static final Map<Long, String> currentInput = new ConcurrentHashMap<>();

    private static final float DARK_MODE_BUTTON_WIDTH = 40.0f;
    private static final float DARK_MODE_BUTTON_HEIGHT = 20.0f;
    private static final float DARK_MODE_BUTTON_PADDING = 10.0f;
    private static final float NEW_TAB_BUTTON_WIDTH = 20.0f;

    private static final Map<Long, Integer> previousTabIndices = new ConcurrentHashMap<>();

    private static final Map<Long, Integer> renamingTabs = new ConcurrentHashMap<>();

    private static InputMode currentInputMode = InputMode.NORMAL;
    private static String sessionBeingRenamed = null;

    private static final Map<Long, Map<TextBubble, AnimationState>> bubbleAnimations = new ConcurrentHashMap<>();
    private static final long ANIMATION_DURATION = 300; // milliseconds

    private static final float SESSION_BUTTON_WIDTH = 40.0f;
    private static final float SESSION_BUTTON_PADDING = 10.0f;
    private static final float SESSION_MENU_WIDTH = 200.0f;  // Wider menu
    private static final float SESSION_MENU_ITEM_HEIGHT = 30.0f;  // Taller menu items

    private static final float ITALIC_SKEW = 0.25f;  // Controls italic slant
    private static final float BOLD_OFFSET = 1.0f;   // Controls bold thickness

    private static final float BASE_WINDOW_WIDTH = 1024.0f;  // Base size for scaling calculations
    private static final float BASE_WINDOW_HEIGHT = 600.0f;
    private static final float MIN_SCALE = 0.75f;
    private static final float MAX_SCALE = 2.0f;




    // Inner Classes
    private static File getConfigFile() {
        return new File(getWindowsClientConfigDir(), "ui_state.json");
    }

    public static class UIState {
        boolean isDarkMode;
        float scale;
        Color primaryColor;
        Color accentColor;
        Color textColor;
        Color backgroundColor;
        Color bubbleColor;  // Add bubble color to UIState
        int windowWidth;
        int windowHeight;

        UIState() {
            isDarkMode = true;
            scale = 1.0f;
            windowWidth = 800;
            windowHeight = 600;
            updateColors();
        }

        void toggleDarkMode() {
            isDarkMode = !isDarkMode;
            updateColors();

            // Update all existing bubbles to match new theme
            for (TabInfo tab : UI.getWindowTabs()) {
                for (TextBubble bubble : tab.bubbles) {
                    if (isDarkMode) {
                        bubble.colorR = 0.2f;
                        bubble.colorG = 0.2f;
                        bubble.colorB = 0.2f;
                    } else {
                        bubble.colorR = 0.85f;
                        bubble.colorG = 0.85f;
                        bubble.colorB = 0.85f;
                    }
                    bubble.colorA = 1.0f;
                }
            }

            // Save state
            try {
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Color.class, new ColorAdapter())
                        .setPrettyPrinting()
                        .create();
                File stateFile = new File(getWindowsClientConfigDir(), "ui_state.json");
                try (FileWriter writer = new FileWriter(stateFile)) {
                    writer.write(gson.toJson(this));
                }
            } catch (Exception e) {
                System.err.println("Error saving UI state: " + e.getMessage());
                e.printStackTrace();
            }
        }

        void updateColors() {
            if (isDarkMode) {
                backgroundColor = new Color(0.15f, 0.15f, 0.15f, 1.0f);  // Lighter dark gray
                primaryColor = new Color(0.2f, 0.2f, 0.2f, 1.0f);       // Lighter primary
                accentColor = new Color(0.4f, 0.6f, 1.0f, 1.0f);        // Brighter blue
                textColor = new Color(0.95f, 0.95f, 0.95f, 1.0f);       // Brighter white
                bubbleColor = new Color(0.25f, 0.25f, 0.25f, 1.0f);     // Lighter bubble
            } else {
                backgroundColor = new Color(0.95f, 0.95f, 0.95f, 1.0f);  // Light gray
                primaryColor = new Color(1.0f, 1.0f, 1.0f, 1.0f);       // White
                accentColor = new Color(0.4f, 0.6f, 1.0f, 1.0f);        // Bright blue
                textColor = new Color(0.1f, 0.1f, 0.1f, 1.0f);          // Near black
                bubbleColor = new Color(0.85f, 0.85f, 0.85f, 1.0f);     // Light bubble
            }
        }
    }


    public enum InputMode {
        NORMAL,
        SAVING_SESSION,
        RENAMING_SESSION,
        TEXT_INPUT,
        MODAL
    }
    static class TabInfo {
        String name;
        List<TextBubble> bubbles;
        Color color;
        float scrollOffset;

        TabInfo(String name) {
            this.name = name;
            this.bubbles = new ArrayList<>();
            this.color = new Color(0.6f, 0.6f, 0.7f, 0.8f);
            this.scrollOffset = 0.0f;
        }
    }


    private static float calculateDynamicScale(int windowWidth, int windowHeight) {
        float widthScale = windowWidth / BASE_WINDOW_WIDTH;
        float heightScale = windowHeight / BASE_WINDOW_HEIGHT;

        // Use the smaller scale to ensure everything fits
        float scale = Math.min(widthScale, heightScale);

        // Clamp scale between min and max values
        return Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE));
    }

    public static UIState getCurrentState() {
        if (windowStates.isEmpty()) return null;
        return windowStates.values().iterator().next();
    }

    // Add helper method to get all tabs
    public static List<TabInfo> getWindowTabs() {
        List<TabInfo> allTabs = new ArrayList<>();
        for (List<TabInfo> tabs : windowTabs.values()) {
            allTabs.addAll(tabs);
        }
        return allTabs;
    }


    static class AnimationState {
        float startX, startY;
        float targetX, targetY;
        long startTime;
        boolean isAnimating;

        AnimationState(float startX, float startY, float targetX, float targetY) {
            this.startX = startX;
            this.startY = startY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.startTime = System.currentTimeMillis();
            this.isAnimating = true;
        }

        float getCurrentX() {
            if (!isAnimating) return targetX;
            float progress = getProgress();
            return startX + (targetX - startX) * easeOutCubic(progress);
        }

        float getCurrentY() {
            if (!isAnimating) return targetY;
            float progress = getProgress();
            return startY + (targetY - startY) * easeOutCubic(progress);
        }

        float getProgress() {
            float elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1.0f, elapsed / ANIMATION_DURATION);
            if (progress >= 1.0f) isAnimating = false;
            return progress;
        }

        // Smooth easing function
        private float easeOutCubic(float x) {
            return 1 - (float)Math.pow(1 - x, 3);
        }
    }

    public static void handleResize(long window, int newWidth, int newHeight) {
        UIState state = windowStates.get(window);
        if (state == null) return;

        // Update window dimensions
        state.windowWidth = newWidth;
        state.windowHeight = newHeight;

        // Recalculate bubble positions with animations
        List<TabInfo> tabs = windowTabs.get(window);
        if (tabs == null) return;

        for (TabInfo tab : tabs) {
            repositionBubblesWithAnimation(window, tab);
        }
    }

    private static void repositionBubblesWithAnimation(long window, TabInfo tab) {
        // Start from the top of the window under the tab bar
        float currentY = TAB_HEIGHT + 10;
        Map<TextBubble, AnimationState> animations = bubbleAnimations.computeIfAbsent(window, k -> new ConcurrentHashMap<>());

        // Go through each bubble and position it properly
        for (TextBubble bubble : tab.bubbles) {
            float targetX = 10; // Default X position
            float targetY = currentY;

            // Create animation if position changed significantly (more than 1 pixel)
            if (Math.abs(bubble.x - targetX) > 1 || Math.abs(bubble.y - targetY) > 1) {
                animations.put(bubble, new AnimationState(bubble.x, bubble.y, targetX, targetY));
            }

            // Calculate proper height for this bubble
            float bubbleHeight = calculateBubbleHeight(bubble);

            // Move down for next bubble with proper spacing
            currentY += bubbleHeight + BUBBLE_SPACING;
        }
    }

    private static float calculateBubbleHeight(TextBubble bubble) {
        float textHeight = getTextHeight(bubble.scale);
        float timestampHeight = getTextHeight(bubble.scale * 0.8f);
        return Math.max(textHeight + 10, timestampHeight) + 25; // Added more padding
    }

    static class TextBubble {
        float x;
        float y;
        String text;
        float scale;
        String timestamp;
        boolean isEditing;

        // Color components as floats (0.0f to 1.0f)
        float colorR = 0.95f;  // Default light blue
        float colorG = 0.95f;
        float colorB = 1.0f;
        float colorA = 0.9f;

        BubbleStyle style;
        boolean isBold;
        boolean isItalic;

        enum BubbleStyle {
            RECTANGLE,
            ROUNDED,
            SPEECH,
            THOUGHT
        }

        // Custom Color class to match ColorUtils
        static class Color {
            float r, g, b, a;

            Color(float r, float g, float b, float a) {
                this.r = r;
                this.g = g;
                this.b = b;
                this.a = a;
            }
        }

        TextBubble(String text, float scale) {
            this.text = text != null ? text : "";
            this.scale = scale;
            this.x = 0;
            this.y = 0;
            this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.isEditing = false;
            this.style = BubbleStyle.ROUNDED;
            this.isBold = false;
            this.isItalic = false;

            // Set default colors based on current UI state
            UIState state = windowStates.values().iterator().next(); // Get current state
            if (state != null && state.isDarkMode) {
                this.colorR = 0.2f;  // Darker bubble color for dark mode
                this.colorG = 0.2f;
                this.colorB = 0.2f;
                this.colorA = 0.9f;
            } else {
                this.colorR = 0.95f; // Light bubble color for light mode
                this.colorG = 0.95f;
                this.colorB = 1.0f;
                this.colorA = 0.9f;
            }
        }
    }


    static class DragState {
        TextBubble bubble;
        double startX;
        double startY;
        double offsetX;
        double offsetY;
    }

    // Initialize
    public static void initializeWindow(long window) {
        if (window == 0) {
            System.err.println("Error: Invalid window handle");
            return;
        }

        UIState state = loadUiState();
        if (state == null) {
            System.err.println("Error: Failed to load UI state, using default");
            state = new UIState(); // Fallback to a default state
        }

        if (windowStates == null) {
            System.err.println("Error: windowStates map is not initialized");
            windowStates = new ConcurrentHashMap<>();
        }

        windowStates.put(window, state);

        // Initialize other collections with null checks
        windowTabs.putIfAbsent(window, new ArrayList<>(Collections.singletonList(new TabInfo("Main"))));
        activeTabIndices.putIfAbsent(window, 0);
        inputBuffers.putIfAbsent(window, new StringBuilder());
        messageHistory.putIfAbsent(window, new ArrayList<>());
        historyIndices.putIfAbsent(window, -1);
        cursorPositions.putIfAbsent(window, 0);

        setupCallbacks(window);
    }

    private static void saveUiState() throws IOException {
        File stateFile = new File(
                FabricLoader.getInstance().getConfigDir().toFile(),
                "ui_state.json"
        );

        try (FileWriter writer = new FileWriter(stateFile)) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Color.class, new ColorAdapter())
                    .setPrettyPrinting()
                    .create();
            gson.toJson(windowStates, writer);  // Note: You'll need to handle saving all window states
        }
    }


    private static void handleKeyCallback(long window, int key, int scancode, int action, int mods) {
        try {
            if (action != GLFW_PRESS && action != GLFW_REPEAT) return;

            // Handle Ctrl+S for session menu
            if (key == GLFW_KEY_S && (mods & GLFW_MOD_CONTROL) != 0 && action == GLFW_PRESS) {
                // Get window dimensions for positioning
                int[] width = new int[1];
                int[] height = new int[1];
                glfwGetWindowSize(window, width, height);

                // Position menu in the center of the window
                float menuX = width[0] / 2.0f - MENU_WIDTH / 2.0f;
                float menuY = height[0] / 2.0f - 100;  // Offset from center

                showSessionManagementMenu(window, menuX, menuY);
                return;
            }

            // Get the current input buffer
            StringBuilder input = inputBuffers.computeIfAbsent(window, k -> new StringBuilder());
            int cursorPos = cursorPositions.getOrDefault(window, input.length());

            // Check if a tab is being renamed
            Integer renamingTabIndex = renamingTabs.get(window);

            // Check if a bubble is being edited
            List<TabInfo> tabs = windowTabs.get(window);
            int activeTab = activeTabIndices.getOrDefault(window, 0);
            TextBubble editingBubble = null;

            if (tabs != null && activeTab < tabs.size()) {
                TabInfo currentTab = tabs.get(activeTab);
                editingBubble = currentTab.bubbles.stream()
                        .filter(bubble -> bubble.isEditing)
                        .findFirst()
                        .orElse(null);
            }

            // Renaming tab takes precedence
            boolean hasInput = !input.toString().trim().isEmpty();
            if (renamingTabIndex != null) {
                handleRenamingTabInput(window, key, input, cursorPos, tabs, renamingTabIndex, hasInput);
                return;
            }

            // Session handling takes priority after tab renaming
            if (currentInputMode == InputMode.SAVING_SESSION || currentInputMode == InputMode.RENAMING_SESSION) {
                handleSessionInput(window, key, input, cursorPos);
                return;
            }

            // Bubble editing takes next priority
            if (editingBubble != null) {
                handleBubbleEditInput(window, key, input, editingBubble, hasInput);
                return;
            }

            // Normal input handling
            handleNormalInput(window, key, input, cursorPos);

        } catch (Exception e) {
            System.err.println("Error in handleKeyCallback: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleRenamingTabInput(long window, int key, StringBuilder input, int cursorPos,
                                               List<TabInfo> tabs, int renamingTabIndex, boolean hasInput) {
        switch (key) {
            case GLFW_KEY_ENTER:
                if (tabs != null && hasInput) {
                    tabs.get(renamingTabIndex).name = input.toString().trim();
                }
                renamingTabs.remove(window);
                input.setLength(0);
                cursorPositions.remove(window);
                break;

            case GLFW_KEY_ESCAPE:
                renamingTabs.remove(window);
                input.setLength(0);
                cursorPositions.remove(window);
                break;

            default:
                handleCommonKeyInput(window, key, input, cursorPos);
                break;
        }
    }

    private static void handleSessionInput(long window, int key, StringBuilder input, int cursorPos) {
        switch (key) {
            case GLFW_KEY_ENTER:
                handleSessionEnter(window, input);
                break;

            case GLFW_KEY_ESCAPE:
                handleSessionEscape(window, input);
                break;

            default:
                handleCommonKeyInput(window, key, input, cursorPos);
                break;
        }
    }

    private static void handleSessionEnter(long window, StringBuilder input) {
        if (currentInputMode == InputMode.SAVING_SESSION) {
            String sessionName = input.toString().trim();
            if (!sessionName.isEmpty() && !sessionName.equals("Session name")) {
                List<TabInfo> tabs = windowTabs.get(window);
                if (tabs != null) {
                    SessionManager.saveSession(window, tabs, sessionName);
                }
            }
            input.setLength(0);
            currentInputMode = InputMode.NORMAL;
        } else if (currentInputMode == InputMode.RENAMING_SESSION) {
            String newName = input.toString().trim();
            if (!newName.isEmpty() && sessionBeingRenamed != null) {
                SessionManager.renameSession(sessionBeingRenamed, newName);
                sessionBeingRenamed = null;
            }
            input.setLength(0);
            currentInputMode = InputMode.NORMAL;
        }
        cursorPositions.put(window, 0);
    }

    private static void handleSessionEscape(long window, StringBuilder input) {
        input.setLength(0);
        sessionBeingRenamed = null;
        currentInputMode = InputMode.NORMAL;
        cursorPositions.put(window, 0);
    }

    private static void handleBubbleEditInput(long window, int key, StringBuilder input,
                                              TextBubble bubble, boolean hasInput) {
        if (key == GLFW_KEY_ENTER) {
            if (hasInput) {
                bubble.text = input.toString().trim();
            }
            bubble.isEditing = false;
            input.setLength(0);
            cursorPositions.put(window, 0);
        } else if (key == GLFW_KEY_ESCAPE) {
            bubble.isEditing = false;
            input.setLength(0);
            cursorPositions.put(window, 0);
        }
    }


    private static void handleHistoryNavigation(long window, int key, StringBuilder input) {
        List<String> history = messageHistory.get(window);
        if (history == null || history.isEmpty()) return;

        int historyIndex = historyIndices.getOrDefault(window, -1);

        if (key == GLFW_KEY_UP) {
            if (historyIndex == -1) {
                // Save current input before navigating history
                currentInput.put(window, input.toString());
            }
            if (historyIndex < history.size() - 1) {
                historyIndex++;
                historyIndices.put(window, historyIndex);
                input.setLength(0);
                input.append(history.get(history.size() - 1 - historyIndex));
                cursorPositions.put(window, input.length());
            }
        } else if (key == GLFW_KEY_DOWN) {
            if (historyIndex > 0) {
                historyIndex--;
                historyIndices.put(window, historyIndex);
                input.setLength(0);
                input.append(history.get(history.size() - 1 - historyIndex));
                cursorPositions.put(window, input.length());
            } else if (historyIndex == 0) {
                // Return to saved input
                historyIndex = -1;
                historyIndices.put(window, historyIndex);
                input.setLength(0);
                String savedInput = currentInput.get(window);
                if (savedInput != null) {
                    input.append(savedInput);
                }
                cursorPositions.put(window, input.length());
            }
        }
    }

    private static void handleNormalInput(long window, int key, StringBuilder input, int cursorPos) {
        switch (key) {
            case GLFW_KEY_ENTER:
                if (!input.isEmpty()) {
                    handleEnterPressed(window);
                }
                break;

            case GLFW_KEY_UP:
            case GLFW_KEY_DOWN:
                handleHistoryNavigation(window, key, input);
                break;

            case GLFW_KEY_TAB:
                handleTabNavigation(window);
                break;

            default:
                handleCommonKeyInput(window, key, input, cursorPos);
                break;
        }
    }


    private static void handleTabNavigation(long window) {
        List<TabInfo> tabs = windowTabs.get(window);
        if (tabs != null && tabs.size() > 1) {
            int currentTab = activeTabIndices.getOrDefault(window, 0);

            // Store the current tab as previous before switching
            previousTabIndices.put(window, currentTab);

            // Move to next tab
            int newTab = (currentTab + 1) % tabs.size();
            activeTabIndices.put(window, newTab);

            // Reset scroll position for new tab
            scrollOffsets.put(window, 0.0f);

            System.out.println("Switched to tab: " + tabs.get(newTab).name);
        }
    }

    private static void handleCommonKeyInput(long window, int key, StringBuilder input, int cursorPos) {
        switch (key) {
            case GLFW_KEY_BACKSPACE:
                if (!input.isEmpty() && cursorPos > 0) {
                    input.deleteCharAt(cursorPos - 1);
                    cursorPositions.put(window, cursorPos - 1);
                }
                break;

            case GLFW_KEY_DELETE:
                if (!input.isEmpty() && cursorPos < input.length()) {
                    input.deleteCharAt(cursorPos);
                }
                break;

            case GLFW_KEY_LEFT:
                if (cursorPos > 0) {
                    cursorPositions.put(window, cursorPos - 1);
                }
                break;

            case GLFW_KEY_RIGHT:
                if (cursorPos < input.length()) {
                    cursorPositions.put(window, cursorPos + 1);
                }
                break;

            case GLFW_KEY_HOME:
                cursorPositions.put(window, 0);
                break;

            case GLFW_KEY_END:
                cursorPositions.put(window, input.length());
                break;
        }
    }

    private static void setupCallbacks(long window) {
        // Character input callback
        glfwSetCharCallback(window, UI::handleCharCallback);

        // Mouse button callback
        glfwSetMouseButtonCallback(window, UI::handleMouseButtonCallback);

        // Cursor position callback
        glfwSetCursorPosCallback(window, UI::handleCursorPosCallback);

        // Scroll callback
        glfwSetScrollCallback(window, UI::handleScrollCallback);

        // Window resize callback
        glfwSetWindowSizeCallback(window, UI::handleWindowSizeCallback);

        // Key callback with comprehensive handling
        glfwSetKeyCallback(window, UI::handleKeyCallback);
    }


    static UIState loadUiState() {
        File stateFile = new File(
                getWindowsClientConfigDir(),
                "ui_state.json"
        );

        if (stateFile.exists()) {
            try (FileReader reader = new FileReader(stateFile)) {
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Color.class, new ColorAdapter())
                        .create();
                return gson.fromJson(reader, UIState.class);
            } catch (Exception e) {
                System.err.println("Error loading UI state: " + e.getMessage());
            }
        }

        // Return default state if no saved state exists
        return new UIState();
    }


    // Main render method
    public static void render(long window) {
        UIState state = windowStates.get(window);
        if (state == null) return;

        // Get window dimensions
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);

        // Calculate dynamic scale
        float dynamicScale = calculateDynamicScale(width[0], height[0]);
        state.scale = dynamicScale;

        // Setup render state
        glClearColor(
                state.backgroundColor.getRed() / 255f,
                state.backgroundColor.getGreen() / 255f,
                state.backgroundColor.getBlue() / 255f,
                1.0f
        );
        glClear(GL_COLOR_BUFFER_BIT);

        // Setup projection
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width[0], height[0], 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Draw UI elements with dynamic scale
        drawTabs(window, width[0], height[0]);
        drawMessages(window, width[0], height[0]);
        drawInputArea(window, width[0], height[0]);
        drawContextMenu(window);

        glfwSwapBuffers(window);
    }


    private static void showTabContextMenu(long window, float x, float y, TabInfo tab, int tabIndex) {
        List<MenuItem> tabMenuItems = new ArrayList<>(Arrays.asList(
                new MenuItem("Rename Tab", () -> {
                    // Start renaming for non-main tabs
                    if (tabIndex > 0) {
                        renamingTabs.put(window, tabIndex);
                        // Reset input buffer for renaming
                        StringBuilder input = new StringBuilder(tab.name);
                        inputBuffers.put(window, input);
                        cursorPositions.put(window, input.length());
                    }
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Duplicate Tab", () -> {
                    List<TabInfo> tabs = windowTabs.get(window);
                    if (tabs != null && tabs.size() < 10) {
                        TabInfo duplicatedTab = new TabInfo(tab.name + " (Copy)");
                        // Deep copy bubbles
                        duplicatedTab.bubbles = tab.bubbles.stream()
                                .map(bubble -> new TextBubble(bubble.text, bubble.scale)).collect(Collectors.toList());
                        tabs.add(duplicatedTab);
                        activeTabIndices.put(window, tabs.size() - 1);
                    }
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Close Tab", () -> {
                    List<TabInfo> tabs = windowTabs.get(window);
                    if (tabs != null && tabs.size() > 1 && tabIndex > 0) {
                        tabs.remove(tabIndex);
                        int currentTab = activeTabIndices.getOrDefault(window, 0);
                        if (currentTab >= tabIndex) {
                            activeTabIndices.put(window, Math.max(0, currentTab - 1));
                        }
                    }
                    activeContextMenus.remove(window);
                })
        ));

        // For the main tab, remove the close option
        if (tabIndex == 0) {
            tabMenuItems.removeIf(item -> item.label.equals("Close Tab"));
        }

        // Adjust menu position to ensure it's within window bounds
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);

        float menuX = x;
        float menuY = y;

        // Ensure menu doesn't go off the right side of the screen
        if (menuX + MENU_WIDTH > width[0]) {
            menuX = width[0] - MENU_WIDTH;
        }

        // Ensure menu doesn't go off the bottom of the screen
        if (menuY + (MENU_ITEM_HEIGHT * tabMenuItems.size()) > height[0]) {
            menuY = height[0] - (MENU_ITEM_HEIGHT * tabMenuItems.size());
        }

        ContextMenu tabContextMenu = new ContextMenu(window, menuX, menuY, null);
        tabContextMenu.items = tabMenuItems;
        tabContextMenu.isVisible = true;
        activeContextMenus.put(window, tabContextMenu);
    }

    private static void handleContextMenu(long window, double x, double y) {
        ContextMenu menu = activeContextMenus.get(window);
        if (menu == null || !menu.isVisible) return;

        // Calculate which menu item was clicked
        int itemIndex = (int) ((y - menu.y) / MENU_ITEM_HEIGHT);

        // Check if click is within menu bounds
        if (x >= menu.x && x <= menu.x + MENU_WIDTH &&
                itemIndex >= 0 && itemIndex < menu.items.size()) {
            // Execute the selected menu item's action
            menu.items.get(itemIndex).action.run();
        } else {
            // Click outside menu, close the menu
            activeContextMenus.remove(window);
        }
    }


    // Drawing methods
    private static void drawTabs(long window, int width, int height) {
        UIState state = windowStates.get(window);
        List<TabInfo> tabs = windowTabs.get(window);
        int activeTab = activeTabIndices.getOrDefault(window, 0);
        Integer renamingTabIndex = renamingTabs.get(window);

        if (state == null || tabs == null) return;

        float x = TAB_PADDING;
        float y = 0;

        // Draw tab bar background
        Color tabBarColor = state.isDarkMode ?
                new Color(0.18f, 0.18f, 0.18f, 1.0f) :
                new Color(0.9f, 0.9f, 0.9f, 1.0f);
        drawRect(0, 0, width, TAB_HEIGHT, tabBarColor);

        // Draw tabs
        for (int i = 0; i < tabs.size(); i++) {
            TabInfo tab = tabs.get(i);
            boolean isActive = i == activeTab;
            boolean isRenaming = renamingTabIndex != null && renamingTabIndex == i;

            // Get precise text width for the current tab name
            String displayText = isRenaming ? "Tab Rename" : tab.name;
            float textWidth = getTextWidth(displayText, state.scale);
            float tabWidth = textWidth + 40;  // Add padding

            // Determine tab color
            Color tabColor = isActive ?
                    state.accentColor :
                    new Color(
                            state.isDarkMode ? 0.25f : 0.8f,
                            state.isDarkMode ? 0.25f : 0.8f,
                            state.isDarkMode ? 0.25f : 0.8f,
                            1.0f
                    );

            // Draw tab background
            drawRoundedRect(x, y + 2, tabWidth, TAB_HEIGHT - 4, DEFAULT_CORNER_RADIUS, tabColor);

            // Draw tab text
            drawText(x + 20, y + (TAB_HEIGHT - getTextHeight(state.scale)) / 2,
                    displayText, state.scale, state.textColor);

            // Draw close button for non-main tabs
            if (i > 0 && !isRenaming) {
                float closeX = x + tabWidth - 25;
                float closeY = y + 8;
                drawCloseButton(closeX, closeY, state.textColor);
            }

            x += tabWidth + 5;
        }

        // Draw new tab button if there's room
        if (tabs.size() < 10) {
            drawNewTabButton(x, y + 2, state);
        }

        // Draw dark mode toggle button
        float buttonX = width - DARK_MODE_BUTTON_WIDTH - DARK_MODE_BUTTON_PADDING;
        float buttonY = 5;

        Color buttonColor = state.isDarkMode ?
                new Color(0.25f, 0.25f, 0.25f, 1.0f) :
                new Color(0.7f, 0.7f, 0.7f, 1.0f);

        drawRoundedRect(
                buttonX,
                buttonY,
                DARK_MODE_BUTTON_WIDTH,
                DARK_MODE_BUTTON_HEIGHT,
                5.0f,
                buttonColor
        );

        // Draw the dark mode toggle text
        drawText(
                buttonX + 5,
                buttonY + 3,
                state.isDarkMode ? "D" : "L",
                1.0f,
                state.textColor
        );
    }



    private static void drawMessages(long window, int width, int height) {
        UIState state = windowStates.get(window);
        List<TabInfo> tabs = windowTabs.get(window);
        int activeTab = activeTabIndices.getOrDefault(window, 0);
        float scrollOffset = scrollOffsets.getOrDefault(window, 0.0f);

        if (state == null || tabs == null || activeTab >= tabs.size()) return;

        TabInfo tab = tabs.get(activeTab);
        Map<TextBubble, AnimationState> animations = bubbleAnimations.get(window);

        for (TextBubble bubble : tab.bubbles) {
            // Get current position (either animated or static)
            float x = bubble.x;
            float y = bubble.y - scrollOffset;

            if (animations != null) {
                AnimationState anim = animations.get(bubble);
                if (anim != null && anim.isAnimating) {
                    x = anim.getCurrentX();
                    y = anim.getCurrentY() - scrollOffset;

                    // Update bubble position when animation completes
                    if (!anim.isAnimating) {
                        bubble.x = anim.targetX;
                        bubble.y = anim.targetY;
                        animations.remove(bubble);
                    }
                }
            }

            // Skip if outside visible area
            if (y + getTextHeight(state.scale) < TAB_HEIGHT || y > height - CONTENT_BOTTOM_MARGIN) {
                continue;
            }

            // Draw the bubble at its current position
            drawBubble(window, bubble, x, y, state);
        }
    }


    private static void drawBubble(long window, TextBubble bubble, float x, float y, UIState state) {
        float dynamicScale = state.scale;

        // Calculate dimensions with dynamic scale
        float textWidth = getTextWidth(bubble.text, bubble.scale * dynamicScale);
        float timestampWidth = getTextWidth(bubble.timestamp, bubble.scale * 0.8f * dynamicScale);
        float bubbleWidth = Math.max(textWidth, timestampWidth) + 40 * dynamicScale;
        float bubbleHeight = getTextHeight(bubble.scale * dynamicScale) + 25 * dynamicScale;

        // Draw bubble background
        Color bubbleColor = new Color(bubble.colorR, bubble.colorG, bubble.colorB, bubble.colorA);

        switch (bubble.style) {
            case RECTANGLE:
                drawRect(x, y, bubbleWidth, bubbleHeight, bubbleColor);
                break;
            case ROUNDED:
            default:
                drawRoundedRect(x, y, bubbleWidth, bubbleHeight,
                        DEFAULT_CORNER_RADIUS * dynamicScale, bubbleColor);
                break;
        }

        // Draw message text with dynamic scale
        drawStyledText(x + 20 * dynamicScale, y + 5 * dynamicScale,
                bubble.text, bubble.scale * dynamicScale, state.textColor,
                bubble.isBold, bubble.isItalic);

        // Draw timestamp
        Color timeColor = new Color(100, 100, 100);
        drawText(x + bubbleWidth - timestampWidth - 15 * dynamicScale,
                y + bubbleHeight - 20 * dynamicScale,
                bubble.timestamp,
                bubble.scale * 0.8f * dynamicScale,
                timeColor);
    }


    private static void drawInputArea(long window, int width, int height) {
        UIState state = windowStates.get(window);
        StringBuilder input = inputBuffers.get(window);
        int cursorPos = cursorPositions.getOrDefault(window, 0);

        if (state == null || input == null) return;

        float dynamicScale = state.scale;
        float scaledHeight = TEXT_BOX_HEIGHT * dynamicScale;
        float scaledPadding = 10 * dynamicScale;

        // Calculate input Y position with dynamic scale
        float inputY = height - scaledHeight - scaledPadding;
        float textBoxWidth = TEXT_BOX_WIDTH * dynamicScale;
        float buttonWidth = BUTTON_WIDTH * dynamicScale;

        // Draw input box background
        drawRoundedRect(TEXT_BOX_X * dynamicScale, inputY,
                textBoxWidth, scaledHeight,
                DEFAULT_CORNER_RADIUS * dynamicScale, state.primaryColor);

        // Draw input text with dynamic scale
        String text = input.toString();
        drawText(TEXT_BOX_X * dynamicScale + scaledPadding,
                inputY + scaledPadding,
                text, dynamicScale, state.textColor);

        // Draw cursor with dynamic scale
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            float cursorX = TEXT_BOX_X * dynamicScale + scaledPadding +
                    getTextWidth(text.substring(0, cursorPos), dynamicScale);
            drawRect(cursorX, inputY + scaledPadding,
                    2 * dynamicScale, scaledHeight - 2 * scaledPadding,
                    state.textColor);
        }

        // Draw send button with dynamic scale
        float sendButtonX = TEXT_BOX_X * dynamicScale + textBoxWidth + scaledPadding;
        drawRoundedRect(sendButtonX, inputY,
                buttonWidth, scaledHeight,
                DEFAULT_CORNER_RADIUS * dynamicScale, state.accentColor);

        // Draw send text
        drawText(sendButtonX + (buttonWidth - getTextWidth("Send", dynamicScale)) / 2,
                inputY + (scaledHeight - getTextHeight(dynamicScale)) / 2,
                "Send", dynamicScale, state.textColor);

        // Draw session button with dynamic scale
        float sessionButtonX = sendButtonX + buttonWidth + scaledPadding;
        float sessionButtonWidth = 40.0f * dynamicScale;
        drawRoundedRect(sessionButtonX, inputY,
                sessionButtonWidth, scaledHeight,
                DEFAULT_CORNER_RADIUS * dynamicScale, state.accentColor);

        // Draw dots for session button
        float dotSize = 4.0f * dynamicScale;
        float spacing = 6.0f * dynamicScale;
        float startY = inputY + (scaledHeight / 2) - spacing;
        float centerX = sessionButtonX + (sessionButtonWidth / 2);

        for (int i = 0; i < 3; i++) {
            drawFilledCircle(centerX,
                    startY + (i * spacing),
                    dotSize / 2,
                    state.textColor);
        }
    }

    // Helper drawing methods
    private static void drawRect(float x, float y, float width, float height, Color color) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }

    private static void drawRoundedRect(float x, float y, float width, float height,
                                        float radius, Color color) {
        float x2 = x + width;
        float y2 = y + height;
        int segments = 16;

        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);

        glBegin(GL_POLYGON);

        // Top-left corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI / 2 * i / segments);
            glVertex2f(x + radius - (float)Math.cos(angle) * radius,
                    y + radius - (float)Math.sin(angle) * radius);
        }

        // Top-right corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI / 2 * i / segments);
            glVertex2f(x2 - radius + (float)Math.sin(angle) * radius,
                    y + radius - (float)Math.cos(angle) * radius);
        }

        // Bottom-right corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI / 2 * i / segments);
            glVertex2f(x2 - radius + (float)Math.cos(angle) * radius,
                    y2 - radius + (float)Math.sin(angle) * radius);
        }

        // Bottom-left corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI / 2 * i / segments);
            glVertex2f(x + radius - (float)Math.sin(angle) * radius,
                    y2 - radius + (float)Math.cos(angle) * radius);
        }

        glEnd();
    }

    private static void drawText(float x, float y, String text, float scale, Color color) {
        if (text == null || text.isEmpty()) return;

        text = Normalizer.normalize(text, Normalizer.Form.NFC);

        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);

        glPushMatrix();
        glTranslatef(x, y, 0f);
        glScalef(scale * 2.0f, scale * 2.0f, 1f);

        try (MemoryStack stack = stackPush()) {
            ByteBuffer charBuffer = stack.malloc((text.length() * 4 + 1) * 300);
            int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, charBuffer);

            glEnableClientState(GL_VERTEX_ARRAY);
            glVertexPointer(2, GL_FLOAT, 16, charBuffer);
            glDrawArrays(GL_QUADS, 0, quads * 4);
            glDisableClientState(GL_VERTEX_ARRAY);
        }

        glPopMatrix();
    }

    private static void drawCloseButton(float x, float y, Color color) {
        float lineWidth = 2.0f;
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);


        glPushMatrix();
        glTranslatef(x + (float) 14 /2, y + (float) 14 /2, 0);
        glRotatef(45, 0, 0, 1);

        glBegin(GL_QUADS);
        // Horizontal line
        glVertex2f(-(float) 14 /2, -lineWidth/2);
        glVertex2f((float) 14 /2, -lineWidth/2);
        glVertex2f((float) 14 /2, lineWidth/2);
        glVertex2f(-(float) 14 /2, lineWidth/2);

        // Vertical line
        glVertex2f(-lineWidth/2, -(float) 14 /2);
        glVertex2f(lineWidth/2, -(float) 14 /2);
        glVertex2f(lineWidth/2, (float) 14 /2);
        glVertex2f(-lineWidth/2, (float) 14 /2);
        glEnd();

        glPopMatrix();
    }

    // Input handling callbacks


    private static void handleCharCallback(long window, int codepoint) {

        // Existing character input handling for message input
        StringBuilder input = inputBuffers.get(window);
        if (input == null || input.length() >= MAX_INPUT_LENGTH) return;

        int cursorPos = cursorPositions.getOrDefault(window, input.length());
        input.insert(cursorPos, (char) codepoint);
        cursorPositions.put(window, cursorPos + 1);
    }

    private static void handleRightClick(long window, double x, double y) {
        // Check tabs first
        if (y < TAB_HEIGHT) {
            List<TabInfo> tabs = windowTabs.get(window);
            if (tabs != null) {
                float currentX = TAB_PADDING;
                for (int i = 0; i < tabs.size(); i++) {
                    TabInfo tab = tabs.get(i);
                    float tabWidth = getTextWidth(tab.name, 1.0f) + 40;

                    if (x >= currentX && x <= currentX + tabWidth) {
                        // Right-click on a tab
                        showTabContextMenu(window, (float)x, (float)y, tab, i);
                        return;
                    }

                    currentX += tabWidth + 5;
                }
            }
        }

        // Then check bubbles
        TextBubble bubble = findBubbleAtPosition(window, x, y);
        if (bubble != null) {
            showContextMenu(window, (float)x, (float)y, bubble);
        }
    }

    static class ContextMenu {
        float x, y;
        TextBubble targetBubble;
        boolean isVisible;
        List<MenuItem> items;

        ContextMenu(long window, float x, float y, TextBubble bubble) {
            this.x = x;
            this.y = y;
            this.targetBubble = bubble;
            this.isVisible = true;
            this.items = Arrays.asList(
                    new MenuItem("Edit", () -> startEditing(window, bubble)),
                    new MenuItem("Style", () -> showStyleMenu(window, x, y + MENU_ITEM_HEIGHT, bubble)),
                    new MenuItem("Color", () -> showColorMenu(window, x, y + MENU_ITEM_HEIGHT, bubble)),
                    new MenuItem("Delete", () -> deleteBubble(window, bubble))
            );
        }
    }

    // Color menu for bubbles using ColorConfig
    private static void showColorMenu(long window, float x, float y, TextBubble bubble) {
        List<MenuItem> colorItems = new ArrayList<>();

        // Define some nice preset colors
        Object[][] presetColors = {
                {"Red", 1.0f, 0.0f, 0.0f},
                {"Green", 0.0f, 1.0f, 0.0f},
                {"Blue", 0.0f, 0.0f, 1.0f},
                {"Yellow", 1.0f, 1.0f, 0.0f},
                {"Cyan", 0.0f, 1.0f, 1.0f},
                {"Magenta", 1.0f, 0.0f, 1.0f},
                {"Black", 0.0f, 0.0f, 0.0f},
                {"White", 1.0f, 1.0f, 1.0f},
                {"Gray", 0.5f, 0.5f, 0.5f},
                {"Orange", 1.0f, 0.5f, 0.0f}
        };


        // Add preset colors to menu
        for (Object[] colorData : presetColors) {
            String name = (String) colorData[0];
            float r = (float) colorData[1];
            float g = (float) colorData[2];
            float b = (float) colorData[3];

            colorItems.add(new MenuItem(name, () -> {
                bubble.colorR = r;
                bubble.colorG = g;
                bubble.colorB = b;
                bubble.colorA = 0.9f;
                activeContextMenus.remove(window);
            }));
        }

        // Create and show the menu
        ContextMenu colorMenu = new ContextMenu(window, x, y, bubble);
        colorMenu.items = colorItems;
        colorMenu.isVisible = true;
        activeContextMenus.put(window, colorMenu);
    }

    private static String getColorNameFromHex(String hexColor) {
        return switch (hexColor) {
            case "#FF0000" -> "Red";
            case "#00FF00" -> "Green";
            case "#0000FF" -> "Blue";
            case "#FFFF00" -> "Yellow";
            case "#FF00FF" -> "Magenta";
            case "#00FFFF" -> "Cyan";
            default -> "Custom Color";
        };
    }




    private static void showStyleMenu(long window, float x, float y, TextBubble bubble) {
        List<MenuItem> styleItems = Arrays.asList(
                new MenuItem("Rectangle", () -> {
                    bubble.style = TextBubble.BubbleStyle.RECTANGLE;
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Rounded", () -> {
                    bubble.style = TextBubble.BubbleStyle.ROUNDED;
                    activeContextMenus.remove(window);
                }),
                new MenuItem(bubble.isBold ? "Disable Bold" : "Enable Bold", () -> {
                    bubble.isBold = !bubble.isBold;
                    activeContextMenus.remove(window);
                }),
                new MenuItem(bubble.isItalic ? "Disable Italic" : "Enable Italic", () -> {
                    bubble.isItalic = !bubble.isItalic;
                    activeContextMenus.remove(window);
                })
        );

        ContextMenu styleMenu = new ContextMenu(window, x, y, null);
        styleMenu.items = styleItems;
        styleMenu.isVisible = true;
        activeContextMenus.put(window, styleMenu);
    }



    static class MenuItem {
        String label;
        Runnable action;

        MenuItem(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private static void showContextMenu(long window, float x, float y, TextBubble bubble) {
        List<MenuItem> bubbleMenuItems = new ArrayList<>(Arrays.asList(
                new MenuItem("Edit", () -> {
                    // Start editing the bubble
                    bubble.isEditing = true;

                    // Get the current active tab
                    List<TabInfo> tabs = windowTabs.get(window);
                    int currentTab = activeTabIndices.getOrDefault(window, 0);

                    if (tabs != null && currentTab < tabs.size()) {
                        // Prepare input for editing
                        StringBuilder input = inputBuffers.get(window);
                        if (input != null) {
                            input.setLength(0);
                            input.append(bubble.text);
                            cursorPositions.put(window, input.length());
                        }
                    }

                    activeContextMenus.remove(window);
                }),
                new MenuItem("Style", () -> {
                    // Open style submenu
                    showStyleMenu(window, x, y + MENU_ITEM_HEIGHT, bubble);
                }),
                new MenuItem("Color", () -> {
                    // Open color submenu
                    showColorMenu(window, x, y + MENU_ITEM_HEIGHT, bubble);
                }),
                new MenuItem("Delete", () -> {
                    // Delete the bubble from its tab
                    List<TabInfo> tabs = windowTabs.get(window);
                    int currentTab = activeTabIndices.getOrDefault(window, 0);
                    if (tabs != null && currentTab < tabs.size()) {
                        TabInfo activeTab = tabs.get(currentTab);
                        activeTab.bubbles.remove(bubble);
                    }
                    activeContextMenus.remove(window);
                })
        ));

        // Adjust menu position to ensure it's within window bounds
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);

        float menuX = x;
        float menuY = y;

        // Ensure menu doesn't go off the right side of the screen
        if (menuX + MENU_WIDTH > width[0]) {
            menuX = width[0] - MENU_WIDTH;
        }

        // Ensure menu doesn't go off the bottom of the screen
        if (menuY + (MENU_ITEM_HEIGHT * bubbleMenuItems.size()) > height[0]) {
            menuY = height[0] - (MENU_ITEM_HEIGHT * bubbleMenuItems.size());
        }

        ContextMenu bubbleContextMenu = new ContextMenu(window, menuX, menuY, bubble);
        bubbleContextMenu.items = bubbleMenuItems;
        bubbleContextMenu.isVisible = true;
        activeContextMenus.put(window, bubbleContextMenu);
    }


    private static void startEditing(long window, TextBubble bubble) {
        if (bubble != null) {
            editingBubbles.put(window, bubble);
            StringBuilder input = inputBuffers.get(window);
            if (input != null) {
                input.setLength(0);
                input.append(bubble.text);
                cursorPositions.put(window, input.length());
            }
        }
        activeContextMenus.remove(window);
    }

    private static void deleteBubble(long window, TextBubble bubble) {
        List<TabInfo> tabs = windowTabs.get(window);
        int currentTab = activeTabIndices.getOrDefault(window, 0);
        if (tabs != null && currentTab < tabs.size()) {
            TabInfo activeTab = tabs.get(currentTab);
            if (activeTab.bubbles != null) {
                activeTab.bubbles.remove(bubble);
            }
        }
        activeContextMenus.remove(window);
    }


    private static void drawContextMenu(long window) {
        ContextMenu menu = activeContextMenus.get(window);
        if (menu == null || !menu.isVisible) return;

        UIState state = windowStates.get(window);
        if (state == null) return;

        // Background color based on theme
        Color menuBg = state.isDarkMode ?
                new Color(0.2f, 0.2f, 0.2f, 0.95f) :
                new Color(0.95f, 0.95f, 0.95f, 0.95f);

        // Draw menu background
        drawRoundedRect(menu.x, menu.y, MENU_WIDTH,
                SESSION_MENU_ITEM_HEIGHT * menu.items.size(),
                5.0f, menuBg);

        // Draw menu items
        float itemY = menu.y;
        for (MenuItem item : menu.items) {
            if ("---".equals(item.label)) {
                // Draw separator
                drawRect(menu.x + 10, itemY + SESSION_MENU_ITEM_HEIGHT/2,
                        MENU_WIDTH - 20, 1, state.textColor);
            } else {
                // Highlight if mouse is over
                if (isMouseOverMenuItem(window, menu.x, itemY, MENU_WIDTH, SESSION_MENU_ITEM_HEIGHT)) {
                    Color highlightColor = state.isDarkMode ?
                            new Color(0.3f, 0.3f, 0.3f, 0.95f) :
                            new Color(0.85f, 0.85f, 0.85f, 0.95f);
                    drawRoundedRect(menu.x, itemY, MENU_WIDTH,
                            SESSION_MENU_ITEM_HEIGHT, 5.0f, highlightColor);
                }

                // Draw item text
                drawText(menu.x + 10, itemY + 7, item.label, 1.0f, state.textColor);

                // Draw arrow for items with submenus
                if (item.label.equals("Sessions")) {
                    float arrowX = menu.x + MENU_WIDTH - 20;
                    float arrowY = itemY + SESSION_MENU_ITEM_HEIGHT/2;
                    drawArrow(arrowX, arrowY, state.textColor);
                }
            }
            itemY += SESSION_MENU_ITEM_HEIGHT;
        }
    }

    private static void drawArrow(float x, float y, Color color) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);

        float size = 6.0f;
        glBegin(GL_TRIANGLES);
        glVertex2f(x, y - size);
        glVertex2f(x + size, y);
        glVertex2f(x, y + size);
        glEnd();
    }

    private static void handleContextMenuClick(long window, double x, double y) {
        ContextMenu menu = activeContextMenus.get(window);
        if (menu == null || !menu.isVisible) return;

        System.out.println("Handling menu click at: " + x + ", " + y);

        // Calculate which menu item was clicked
        float itemY = menu.y;
        for (int i = 0; i < menu.items.size(); i++) {
            MenuItem item = menu.items.get(i);

            // Skip separators
            if ("---".equals(item.label)) {
                itemY += SESSION_MENU_ITEM_HEIGHT;
                continue;
            }

            // Check if click is within this item's bounds
            if (x >= menu.x && x <= menu.x + MENU_WIDTH &&
                    y >= itemY && y <= itemY + SESSION_MENU_ITEM_HEIGHT) {

                System.out.println("Clicked menu item: " + item.label);

                if (item.action != null) {
                    item.action.run();
                    return;
                }
            }
            itemY += SESSION_MENU_ITEM_HEIGHT;
        }

        // Check if click is completely outside both main menu and submenu areas
        boolean outsideMainMenu = x < menu.x || x > menu.x + MENU_WIDTH ||
                y < menu.y || y > menu.y + (menu.items.size() * SESSION_MENU_ITEM_HEIGHT);

        boolean outsideSubMenu = x < menu.x || x > menu.x + MENU_WIDTH * 2 || // Account for submenu width
                y < menu.y || y > menu.y + (menu.items.size() * SESSION_MENU_ITEM_HEIGHT);

        if (outsideMainMenu && outsideSubMenu) {
            System.out.println("Click outside menu, closing");
            activeContextMenus.remove(window);
        }
    }

    private static void handleSubMenuClick(long window, double x, double y) {
        ContextMenu menu = activeContextMenus.get(window);
        if (menu == null || !menu.isVisible || menu.items == null) return;

        // Calculate which menu item was clicked
        float itemY = menu.y;
        for (int i = 0; i < menu.items.size(); i++) {
            MenuItem item = menu.items.get(i);

            // Check if click is within this item's bounds
            if (x >= menu.x && x <= menu.x + SESSION_MENU_WIDTH &&
                    y >= itemY && y <= itemY + SESSION_MENU_ITEM_HEIGHT) {

                if (item.action != null) {
                    item.action.run();
                    return;
                }
            }
            itemY += SESSION_MENU_ITEM_HEIGHT;
        }

        // If click was outside menu bounds, close the menu
        if (x < menu.x || x > menu.x + SESSION_MENU_WIDTH ||
                y < menu.y || y > menu.y + (menu.items.size() * SESSION_MENU_ITEM_HEIGHT)) {
            activeContextMenus.remove(window);
        }
    }

        private static boolean isMouseOverMenuItem(long window, float menuX, float itemY, float width, float height) {
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);

        return xpos[0] >= menuX && xpos[0] <= menuX + width &&
                ypos[0] >= itemY && ypos[0] <= itemY + height;
    }

    private static void handleMouseButtonCallback(long window, int button, int action, int mods) {
        if (action == GLFW_PRESS) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            glfwGetCursorPos(window, xpos, ypos);

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                // First check if there's an active menu
                if (activeContextMenus.containsKey(window)) {
                    System.out.println("Menu is active, handling menu click");
                    handleContextMenuClick(window, xpos[0], ypos[0]);
                    return;
                }

                // Get window dimensions
                int[] width = new int[1];
                int[] height = new int[1];
                glfwGetWindowSize(window, width, height);

                float inputY = height[0] - TEXT_BOX_HEIGHT - 10;
                float sendButtonX = TEXT_BOX_X + TEXT_BOX_WIDTH + 10;
                float sessionButtonX = sendButtonX + BUTTON_WIDTH + SESSION_BUTTON_PADDING;

                // Check session button click
                if (xpos[0] >= sessionButtonX &&
                        xpos[0] <= sessionButtonX + SESSION_BUTTON_WIDTH &&
                        ypos[0] >= inputY &&
                        ypos[0] <= inputY + BUTTON_HEIGHT) {

                    System.out.println("Session button clicked!");
                    showSessionManagementMenu(window, sessionButtonX, inputY - 200);
                    return;
                }

                // Handle other clicks
                handleLeftClick(window, xpos[0], ypos[0]);
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                // Handle right-click menu if needed
                handleRightClick(window, xpos[0], ypos[0]);
            }
        } else if (action == GLFW_RELEASE) {
            dragStates.remove(window);
        }
    }

    private static void handleCursorPosCallback(long window, double xpos, double ypos) {
        DragState dragState = dragStates.get(window);
        if (dragState != null) {
            dragState.bubble.x = (float) (xpos - dragState.offsetX);
            dragState.bubble.y = (float) (ypos - dragState.offsetY);
        }
    }

    private static void handleScrollCallback(long window, double xoffset, double yoffset) {
        float currentOffset = scrollOffsets.getOrDefault(window, 0.0f);
        float newOffset = currentOffset - (float)yoffset * SCROLL_SPEED;
        newOffset = Math.max(0, newOffset);
        scrollOffsets.put(window, newOffset);
    }

    private static void handleWindowSizeCallback(long window, int width, int height) {
        // Adjust UI elements based on new window size
        repositionElements(window, width, height);
    }

    private static void handleSendButtonClick(long window) {
        if (currentInputMode == InputMode.SAVING_SESSION) {
            StringBuilder input = inputBuffers.get(window);
            if (input != null && !input.toString().trim().isEmpty() &&
                    !input.toString().equals("Session name") &&
                    !input.toString().equals("Enter session name")) {

                List<TabInfo> tabs = windowTabs.get(window);
                if (tabs != null) {
                    String sessionName = input.toString().trim();
                    System.out.println("Saving session: " + sessionName);
                    SessionManager.saveSession(window, tabs, sessionName);
                }
                input.setLength(0);
                currentInputMode = InputMode.NORMAL;
                cursorPositions.put(window, 0);
            }
        } else if (currentInputMode == InputMode.RENAMING_SESSION) {
            StringBuilder input = inputBuffers.get(window);
            if (input != null && !input.toString().trim().isEmpty() && sessionBeingRenamed != null) {
                String newName = input.toString().trim();
                System.out.println("Renaming session from " + sessionBeingRenamed + " to " + newName);
                SessionManager.renameSession(sessionBeingRenamed, newName);
                sessionBeingRenamed = null;
                input.setLength(0);
                currentInputMode = InputMode.NORMAL;
                cursorPositions.put(window, 0);
            }
        } else {
            handleEnterPressed(window);
        }
    }

    // Mouse click handlers
    private static void handleLeftClick(long windowHandle, double x, double y) {
        try {
            // Get window dimensions and calculate scale
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetWindowSize(windowHandle, width, height);

            UIState state = windowStates.get(windowHandle);
            if (state == null) return;

            float dynamicScale = calculateDynamicScale(width[0], height[0]);
            float scaledPadding = 10 * dynamicScale;

            // Calculate scaled positions
            float inputY = height[0] - (TEXT_BOX_HEIGHT * dynamicScale) - scaledPadding;
            float textBoxWidth = TEXT_BOX_WIDTH * dynamicScale;
            float buttonWidth = BUTTON_WIDTH * dynamicScale;
            float sessionButtonWidth = 40.0f * dynamicScale;

            // Handle dark mode button
            float buttonX = width[0] - (DARK_MODE_BUTTON_WIDTH * dynamicScale) - (DARK_MODE_BUTTON_PADDING * dynamicScale);
            float buttonY = 5 * dynamicScale;
            if (x >= buttonX && x <= buttonX + (DARK_MODE_BUTTON_WIDTH * dynamicScale) &&
                    y >= buttonY && y <= buttonY + (DARK_MODE_BUTTON_HEIGHT * dynamicScale)) {
                if (state != null) {
                    state.toggleDarkMode();
                }
                return;
            }

            // Handle session button click
            float sendButtonX = (TEXT_BOX_X * dynamicScale) + textBoxWidth + scaledPadding;
            float sessionButtonX = sendButtonX + buttonWidth + scaledPadding;
            if (x >= sessionButtonX && x <= sessionButtonX + sessionButtonWidth &&
                    y >= inputY && y <= inputY + (TEXT_BOX_HEIGHT * dynamicScale)) {
                System.out.println("Session button clicked!");
                showSessionManagementMenu(windowHandle, sessionButtonX, inputY - 200 * dynamicScale);
                return;
            }

            // Handle input area clicks
            if (y >= inputY && y <= inputY + (TEXT_BOX_HEIGHT * dynamicScale)) {
                float scaledTextBoxX = TEXT_BOX_X * dynamicScale;
                if (x >= scaledTextBoxX && x <= scaledTextBoxX + textBoxWidth) {
                    // Calculate cursor position based on click position
                    updateCursorPosition(windowHandle, x - scaledTextBoxX - scaledPadding);
                } else if (x >= sendButtonX && x <= sendButtonX + buttonWidth) {
                    handleEnterPressed(windowHandle);
                }
                return;
            }

            // Handle tab clicks
            float scaledTabHeight = TAB_HEIGHT * dynamicScale;
            if (y < scaledTabHeight) {
                handleScaledTabClick(windowHandle, x, y, dynamicScale);
                return;
            }

            // Handle bubble clicks
            TextBubble bubble = findScaledBubbleAtPosition(windowHandle, x, y, dynamicScale);
            if (bubble != null) {
                startDragging(windowHandle, bubble, x, y);
            }
        } catch (Exception e) {
            System.err.println("Error in handleLeftClick: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static TextBubble findScaledBubbleAtPosition(long window, double x, double y, float scale) {
        List<TabInfo> tabs = windowTabs.get(window);
        int activeTab = activeTabIndices.getOrDefault(window, 0);
        float scrollOffset = scrollOffsets.getOrDefault(window, 0.0f);

        if (tabs == null || activeTab >= tabs.size()) return null;

        TabInfo tab = tabs.get(activeTab);
        float currentY = (TAB_HEIGHT * scale) + (10 * scale) - scrollOffset;

        for (TextBubble bubble : tab.bubbles) {
            float textWidth = getTextWidth(bubble.text, bubble.scale * scale) + (40 * scale);
            float timestampWidth = getTextWidth(bubble.timestamp, bubble.scale * 0.8f * scale);
            float bubbleWidth = Math.max(textWidth, timestampWidth) + (40 * scale);
            float bubbleHeight = getTextHeight(bubble.scale * scale) + (25 * scale);

            if (x >= (10 * scale) && x <= (10 * scale) + bubbleWidth &&
                    y >= currentY && y <= currentY + bubbleHeight) {
                return bubble;
            }

            currentY += bubbleHeight + (BUBBLE_SPACING * scale);
        }

        return null;
    }


    private static void handleScaledTabClick(long window, double x, double y, float scale) {
        List<TabInfo> tabs = windowTabs.get(window);
        if (tabs == null) return;

        float currentX = TAB_PADDING * scale;
        for (int i = 0; i < tabs.size(); i++) {
            TabInfo tab = tabs.get(i);
            float tabWidth = (getTextWidth(tab.name, 1.0f) + 40) * scale;

            if (x >= currentX && x <= currentX + tabWidth) {
                // Check close button for non-main tabs
                if (i > 0) {
                    float closeX = currentX + tabWidth - (25 * scale);
                    float closeY = 5 * scale;
                    float closeSize = 14 * scale;

                    if (x >= closeX && x <= closeX + closeSize &&
                            y >= closeY && y <= closeY + closeSize) {
                        tabs.remove(i);
                        int currentTab = activeTabIndices.getOrDefault(window, 0);
                        if (currentTab >= i) {
                            activeTabIndices.put(window, Math.max(0, currentTab - 1));
                        }
                        return;
                    }
                }
                activeTabIndices.put(window, i);
                return;
            }
            currentX += tabWidth + (5 * scale);
        }

        // Check new tab button
        float newTabButtonWidth = NEW_TAB_BUTTON_WIDTH * scale;
        if (tabs.size() < 10 &&
                x >= currentX && x <= currentX + newTabButtonWidth &&
                y >= 2 * scale && y <= (TAB_HEIGHT - 2) * scale) {
            tabs.add(new TabInfo("Tab " + (tabs.size() + 1)));
            activeTabIndices.put(window, tabs.size() - 1);
        }
    }



    //SessionManager INIT

    static class SessionManagementMenu extends ContextMenu {
        private static final float SESSION_MENU_WIDTH = 250.0f;
        private static final float SESSION_ITEM_HEIGHT = 30.0f;

        SessionManagementMenu(long window, float x, float y) {
            super(window, x, y, null);
            this.items = buildSessionItems(window);
        }

        private List<MenuItem> buildSessionItems(long window) {
            List<MenuItem> items = new ArrayList<>();
            List<String> sessions = SessionManager.listSessions();

            // Add "Save Current" option at the top
            items.add(new MenuItem("Save Current Session...", () -> {
                promptSaveSession(window);
                activeContextMenus.remove(window);
            }));

            if (!sessions.isEmpty()) {
                items.add(new MenuItem("---", null)); // Separator

                // Add all existing sessions
                for (String sessionName : sessions) {
                    items.add(new MenuItem(sessionName, () -> {
                        showSessionActionsMenu(window, sessionName);
                        activeContextMenus.remove(window);
                    }));
                }
            }

            return items;
        }
    }

    private static void drawStyledText(float x, float y, String text, float scale, Color color, boolean isBold, boolean isItalic) {
        if (text == null || text.isEmpty()) return;

        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);

        try (MemoryStack stack = stackPush()) {
            ByteBuffer charBuffer = stack.malloc((text.length() * 4 + 1) * 300);

            glPushMatrix();
            glTranslatef(x, y, 0f);
            glScalef(scale * 2.0f, scale * 2.0f, 1f);

            if (isItalic) {
                // Apply italic transform
                float[] matrix = {
                        1.0f, 0.0f, 0.0f, 0.0f,
                        ITALIC_SKEW, 1.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f
                };
                glMultMatrixf(matrix);
            }

            // For bold text, draw multiple times with slight offsets
            if (isBold) {
                // Main text
                int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, charBuffer);
                glEnableClientState(GL_VERTEX_ARRAY);
                glVertexPointer(2, GL_FLOAT, 16, charBuffer);
                glDrawArrays(GL_QUADS, 0, quads * 4);

                // Bold offset passes
                float[][] offsets = {
                        {BOLD_OFFSET, 0},
                        {-BOLD_OFFSET, 0},
                        {0, BOLD_OFFSET},
                        {0, -BOLD_OFFSET}
                };

                for (float[] offset : offsets) {
                    glPushMatrix();
                    glTranslatef(offset[0], offset[1], 0);
                    glVertexPointer(2, GL_FLOAT, 16, charBuffer);
                    glDrawArrays(GL_QUADS, 0, quads * 4);
                    glPopMatrix();
                }
                glDisableClientState(GL_VERTEX_ARRAY);
            } else {
                // Normal text
                int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, charBuffer);
                glEnableClientState(GL_VERTEX_ARRAY);
                glVertexPointer(2, GL_FLOAT, 16, charBuffer);
                glDrawArrays(GL_QUADS, 0, quads * 4);
                glDisableClientState(GL_VERTEX_ARRAY);
            }

            glPopMatrix();
        }
    }



    private static void showSessionManagementMenu(long window, float x, float y) {
        System.out.println("Showing main menu");
        List<MenuItem> menuItems = new ArrayList<>();

        // Add "Sessions" option that will open the session submenu
        menuItems.add(new MenuItem("Sessions", () -> {
            showSessionsSubmenu(window, x + MENU_WIDTH, y);
        }));

        // Create and show main menu
        ContextMenu menu = new ContextMenu(window, x, y, null);
        menu.items = menuItems;
        menu.isVisible = true;
        activeContextMenus.put(window, menu);
    }

    private static void showSessionsSubmenu(long window, float x, float y) {
        System.out.println("Showing sessions submenu");
        List<MenuItem> sessionItems = new ArrayList<>();

        // Add "Save Current" option at the top
        sessionItems.add(new MenuItem("Save Current Session", () -> {
            System.out.println("Save clicked");
            StringBuilder input = inputBuffers.get(window);
            if (input != null) {
                input.setLength(0);
                input.append("Session name");
                cursorPositions.put(window, input.length());
            }
            currentInputMode = InputMode.SAVING_SESSION;
            activeContextMenus.remove(window);
        }));

        sessionItems.add(new MenuItem("---", null));

        // Add existing sessions
        List<String> sessions = SessionManager.listSessions();
        System.out.println("Found " + sessions.size() + " sessions");

        for (String sessionName : sessions) {
            sessionItems.add(new MenuItem(sessionName, () -> {
                System.out.println("Clicked session: " + sessionName);
                float actionMenuX = x + MENU_WIDTH + 5;
                float actionMenuY = y + (sessionItems.indexOf(sessionItems.stream()
                        .filter(item -> item.label.equals(sessionName))
                        .findFirst()
                        .orElse(null)) * SESSION_MENU_ITEM_HEIGHT);

                // Show action menu (Load, Rename, Delete)
                List<MenuItem> actionItems = Arrays.asList(
                        new MenuItem("Load", () -> {
                            System.out.println("Loading session: " + sessionName);
                            SessionManager.WindowSession session = SessionManager.loadSession(sessionName);
                            if (session != null) {
                                loadSessionIntoWindow(window, session);
                            }
                            activeContextMenus.remove(window);
                        }),
                        new MenuItem("Rename", () -> {
                            System.out.println("Starting rename for: " + sessionName);
                            StringBuilder input = inputBuffers.get(window);
                            if (input != null) {
                                input.setLength(0);
                                input.append(sessionName);
                                cursorPositions.put(window, input.length());
                            }
                            currentInputMode = InputMode.RENAMING_SESSION;
                            sessionBeingRenamed = sessionName;
                            activeContextMenus.remove(window);
                        }),
                        new MenuItem("Delete", () -> {
                            System.out.println("Deleting session: " + sessionName);
                            SessionManager.deleteSession(sessionName);
                            activeContextMenus.remove(window);
                        })
                );

                ContextMenu actionMenu = new ContextMenu(window, actionMenuX, actionMenuY, null);
                actionMenu.items = actionItems;
                actionMenu.isVisible = true;
                activeContextMenus.put(window, actionMenu);
            }));
        }

        // Create and show sessions submenu
        ContextMenu sessionMenu = new ContextMenu(window, x, y, null);
        sessionMenu.items = sessionItems;
        sessionMenu.isVisible = true;
        activeContextMenus.put(window, sessionMenu);
    }


    private static void showSessionActionMenu(long window, float x, float y, String sessionName) {
        System.out.println("Showing action menu for session: " + sessionName);
        List<MenuItem> actionItems = Arrays.asList(
                new MenuItem("Load", () -> {
                    System.out.println("Loading session: " + sessionName);
                    SessionManager.WindowSession session = SessionManager.loadSession(sessionName);
                    if (session != null) {
                        loadSessionIntoWindow(window, session);
                        System.out.println("Session loaded with " + session.tabs.size() + " tabs");
                    }
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Rename", () -> {
                    System.out.println("Starting rename for session: " + sessionName);
                    StringBuilder input = inputBuffers.get(window);
                    if (input != null) {
                        input.setLength(0);
                        input.append(sessionName);
                        cursorPositions.put(window, input.length());
                    }
                    currentInputMode = InputMode.RENAMING_SESSION;
                    sessionBeingRenamed = sessionName;
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Delete", () -> {
                    System.out.println("Deleting session: " + sessionName);
                    SessionManager.deleteSession(sessionName);
                    activeContextMenus.remove(window);
                })
        );

        ContextMenu actionMenu = new ContextMenu(window, x, y, null);
        actionMenu.items = actionItems;
        actionMenu.isVisible = true;
        activeContextMenus.put(window, actionMenu);
    }

    private static void loadSessionIntoWindow(long window, SessionManager.WindowSession session) {
        try {
            System.out.println("Loading session into window...");
            // Load tabs
            List<TabInfo> tabs = new ArrayList<>();
            for (TabInfo tab : session.tabs) {
                tabs.add(tab);
            }
            windowTabs.put(window, tabs);
            activeTabIndices.put(window, 0);
            scrollOffsets.put(window, 0.0f);

            // Apply UI state
            UIState state = windowStates.get(window);
            if (state != null) {
                state.isDarkMode = session.isDarkMode;
                state.scale = session.windowScale;
                state.updateColors();
            }
            System.out.println("Session loaded successfully with " + tabs.size() + " tabs");
        } catch (Exception e) {
            System.err.println("Error loading session into window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleSessionKeyInput(long window, int key) {
        if (currentInputMode == InputMode.SAVING_SESSION) {
            if (key == GLFW_KEY_ENTER) {
                StringBuilder input = inputBuffers.get(window);
                if (input != null && !input.toString().trim().isEmpty() &&
                        !input.toString().equals("Enter session name")) {
                    List<TabInfo> tabs = windowTabs.get(window);
                    if (tabs != null) {
                        SessionManager.saveSession(window, tabs, input.toString().trim());
                    }
                    input.setLength(0);
                }
                currentInputMode = InputMode.NORMAL;
                cursorPositions.put(window, 0);
            } else if (key == GLFW_KEY_ESCAPE) {
                StringBuilder input = inputBuffers.get(window);
                if (input != null) {
                    input.setLength(0);
                }
                currentInputMode = InputMode.NORMAL;
                cursorPositions.put(window, 0);
            }
        } else if (currentInputMode == InputMode.RENAMING_SESSION) {
            if (key == GLFW_KEY_ENTER) {
                StringBuilder input = inputBuffers.get(window);
                if (input != null && !input.toString().trim().isEmpty() &&
                        sessionBeingRenamed != null) {
                    SessionManager.renameSession(sessionBeingRenamed, input.toString().trim());
                    sessionBeingRenamed = null;
                    input.setLength(0);
                }
                currentInputMode = InputMode.NORMAL;
                cursorPositions.put(window, 0);
            } else if (key == GLFW_KEY_ESCAPE) {
                StringBuilder input = inputBuffers.get(window);
                if (input != null) {
                    input.setLength(0);
                }
                sessionBeingRenamed = null;
                currentInputMode = InputMode.NORMAL;
                cursorPositions.put(window, 0);
            }
        }
    }

    private static void showSessionActionsMenu(long window, String sessionName) {
        List<MenuItem> actionItems = Arrays.asList(
                new MenuItem("Load", () -> {
                    loadWindowSession(window, sessionName);
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Rename", () -> {
                    promptRenameSession(window, sessionName);
                    activeContextMenus.remove(window);
                }),
                new MenuItem("Delete", () -> {
                    deleteSession(sessionName);
                    activeContextMenus.remove(window);
                })
        );

        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);

        ContextMenu actionsMenu = new ContextMenu(window, (float)xpos[0] + MENU_WIDTH, (float)ypos[0], null);
        actionsMenu.items = actionItems;
        actionsMenu.isVisible = true;
        activeContextMenus.put(window, actionsMenu);
    }

    private static void promptSaveSession(long window) {
        StringBuilder input = inputBuffers.get(window);
        if (input != null) {
            input.setLength(0);
            input.append("Enter session name");
            cursorPositions.put(window, input.length());
        }
        currentInputMode = InputMode.SAVING_SESSION;
    }

    private static void promptRenameSession(long window, String oldName) {
        StringBuilder input = inputBuffers.get(window);
        if (input != null) {
            input.setLength(0);
            input.append(oldName);
            cursorPositions.put(window, input.length());
        }
        currentInputMode = InputMode.RENAMING_SESSION;
        sessionBeingRenamed = oldName;
    }

    private static void handleSessionButtonClick(long window, float x, float y) {
        // Get window dimensions
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);

        // Calculate button position
        float buttonX = TEXT_BOX_X + TEXT_BOX_WIDTH + 10 + BUTTON_WIDTH + 10;
        float inputY = height[0] - TEXT_BOX_HEIGHT - 10;

        // Check if click is within button bounds
        if (x >= buttonX && x <= buttonX + 40.0f &&
                y >= inputY && y <= inputY + BUTTON_HEIGHT) {

            // Show session menu above the button
            float menuX = buttonX;
            float menuY = inputY - 200; // Position menu above the button

            showSessionManagementMenu(window, menuX, menuY);
        }
    }

    private static void loadWindowSession(long window, String sessionName) {
        SessionManager.WindowSession session = SessionManager.loadSession(sessionName);
        if (session != null && session.tabs != null) {
            windowTabs.put(window, new ArrayList<>(session.tabs));
            activeTabIndices.put(window, 0);
            scrollOffsets.put(window, 0.0f);
        }
    }

    private static void deleteSession(String sessionName) {
        try {
            Path sessionFile = Paths.get(
                    WindowsClient.getWindowsClientConfigDir().getPath(),
                    "sessions",
                    sessionName + ".json"
            );
            Files.deleteIfExists(sessionFile);
        } catch (IOException e) {
            System.err.println("Failed to delete session: " + e.getMessage());
        }
    }

    // Add to your drawInputArea method, after drawing the send button


    private static void drawFilledCircle(float centerX, float centerY, float radius, Color color) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);

        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(centerX, centerY);
        int segments = 16;
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2.0f * Math.PI * i / segments);
            float x = centerX + (float) Math.cos(angle) * radius;
            float y = centerY + (float) Math.sin(angle) * radius;
            glVertex2f(x, y);
        }
        glEnd();
    }




    private static void handleEnterPressed(long windowHandle) {
        try {
            // Get and validate input buffer
            StringBuilder input = inputBuffers.get(windowHandle);
            if (input == null) {
                System.err.println("No input buffer for window");
                return;
            }

            // Get input text and validate
            String text = input.toString().trim();
            if (text.isEmpty()) {
                return;
            }

            // Process text with keyword processor
            try {
                text = KeywordProcessor.processKeywords(text);
            } catch (Exception e) {
                System.err.println("Error processing keywords: " + e.getMessage());
                // Continue with original text if keyword processing fails
            }

            // Get and validate tabs
            List<TabInfo> tabs = windowTabs.get(windowHandle);
            if (tabs == null) {
                System.err.println("No tabs found for window");
                return;
            }

            int activeTab = activeTabIndices.getOrDefault(windowHandle, 0);
            if (activeTab >= tabs.size()) {
                System.err.println("Invalid active tab index");
                return;
            }

            // Get active tab and create bubble
            TabInfo tab = tabs.get(activeTab);
            if (tab.bubbles == null) {
                tab.bubbles = new ArrayList<>();
            }

            // Create and position new bubble
            TextBubble bubble = new TextBubble(text, 1.0f);
            float yPos = TAB_HEIGHT + 10;
            if (!tab.bubbles.isEmpty()) {
                TextBubble lastBubble = tab.bubbles.getLast();
                yPos = lastBubble.y + getTextHeight(1.0f) + 15;
            }
            bubble.x = 10;
            bubble.y = yPos;

            // Add bubble to tab
            tab.bubbles.add(bubble);

            // Update message history
            List<String> history = messageHistory.computeIfAbsent(windowHandle, k -> new ArrayList<>());
            history.add(text);
            while (history.size() > 100) {
                history.removeFirst();
            }
            historyIndices.put(windowHandle, -1);

            // Clear input
            input.setLength(0);
            cursorPositions.put(windowHandle, 0);

        } catch (Exception e) {
            System.err.println("Error in handleEnterPressed: ");
            e.printStackTrace();
        }
    }

    // Helper methods
    private static float getTextWidth(String text, float scale) {
        return text.length() * 7f * scale * 2.0f;
    }

    private static float getTextHeight(float scale) {
        return 10f * scale * 2.0f;
    }


    private static void handleTabClick(long windowHandle, double x, double y) {
        try {
            List<TabInfo> tabs = windowTabs.get(windowHandle);
            if (tabs == null) return;

            float currentX = TAB_PADDING;

            // Cycle through tabs to find which one was clicked
            for (int i = 0; i < tabs.size(); i++) {
                TabInfo tab = tabs.get(i);
                float tabWidth = getTextWidth(tab.name, 1.0f) + 40;

                // Check if click is within tab area
                if (x >= currentX && x <= currentX + tabWidth) {
                    // Check close button for non-main tabs
                    if (i > 0) {
                        float closeX = currentX + tabWidth - 25;
                        float closeY = 5;
                        float closeSize = 14;

                        // Check if click is within close button area
                        if (x >= closeX && x <= closeX + closeSize &&
                                y >= closeY && y <= closeY + closeSize) {
                            // Remove the tab
                            tabs.remove(i);

                            // Adjust active tab index if necessary
                            int currentTab = activeTabIndices.getOrDefault(windowHandle, 0);
                            if (currentTab >= i) {
                                activeTabIndices.put(windowHandle, Math.max(0, currentTab - 1));
                            }
                            return;
                        }
                    }

                    // Set active tab if not already active
                    activeTabIndices.put(windowHandle, i);
                    return;
                }

                currentX += tabWidth + 5;
            }

            // Check new tab button
            float newTabButtonX = currentX;
            if (tabs.size() < 10 &&
                    x >= newTabButtonX && x <= newTabButtonX + NEW_TAB_BUTTON_WIDTH &&
                    y >= 2 && y <= TAB_HEIGHT - 2) {
                tabs.add(new TabInfo("Tab " + (tabs.size() + 1)));
                activeTabIndices.put(windowHandle, tabs.size() - 1);
            }
        } catch (Exception e) {
            System.err.println("Error in handleTabClick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static TextBubble findBubbleAtPosition(long window, double x, double y) {
        List<TabInfo> tabs = windowTabs.get(window);
        int activeTab = activeTabIndices.getOrDefault(window, 0);
        float scrollOffset = scrollOffsets.getOrDefault(window, 0.0f);

        if (tabs == null || activeTab >= tabs.size()) return null;

        TabInfo tab = tabs.get(activeTab);
        float currentY = TAB_HEIGHT + 10 - scrollOffset;

        for (TextBubble bubble : tab.bubbles) {
            float textWidth = getTextWidth(bubble.text, bubble.scale) + 40;  // Increased padding
            float timestampWidth = getTextWidth(bubble.timestamp, bubble.scale * 0.8f);
            float bubbleWidth = Math.max(textWidth, timestampWidth) + 40;  // Increased padding
            float bubbleHeight = getTextHeight(bubble.scale) + 25;  // Increased height

            // Check if click is within bubble bounds
            if (x >= 10 && x <= 10 + bubbleWidth &&
                    y >= currentY && y <= currentY + bubbleHeight) {
                return bubble;
            }

            // Move to next bubble's Y position
            currentY += bubbleHeight + BUBBLE_SPACING;
        }

        return null;
    }

    private static void startDragging(long window, TextBubble bubble, double x, double y) {
        DragState dragState = new DragState();
        dragState.bubble = bubble;
        dragState.startX = x;
        dragState.startY = y;
        dragState.offsetX = x - bubble.x;
        dragState.offsetY = y - bubble.y;
        dragStates.put(window, dragState);
    }

    private static void updateCursorPosition(long window, double clickX) {
        StringBuilder input = inputBuffers.get(window);
        if (input == null) return;

        UIState state = windowStates.get(window);
        if (state == null) return;

        float dynamicScale = state.scale;
        float charWidth = 9.0f * dynamicScale * 2.0f; // Approximate width of each character
        int newPos = (int) (clickX / charWidth);
        newPos = Math.max(0, Math.min(newPos, input.length()));
        cursorPositions.put(window, newPos);
    }

    private static void repositionElements(long window, int width, int height) {
        List<TabInfo> tabs = windowTabs.get(window);
        if (tabs == null) return;

        // Adjust bubbles if they're outside the new window bounds
        for (TabInfo tab : tabs) {
            for (TextBubble bubble : tab.bubbles) {
                float bubbleWidth = getTextWidth(bubble.text, bubble.scale) + 20;
                if (bubble.x + bubbleWidth > width) {
                    bubble.x = width - bubbleWidth - 10;
                }
                float maxY = height - CONTENT_BOTTOM_MARGIN;
                if (bubble.y > maxY) {
                    bubble.y = maxY;
                }
            }
        }
    }

    // Cleanup method
    public static void cleanup(long window) {
        windowStates.remove(window);
        windowTabs.remove(window);
        activeTabIndices.remove(window);
        inputBuffers.remove(window);
        scrollOffsets.remove(window);
        dragStates.remove(window);
        messageHistory.remove(window);
        historyIndices.remove(window);
        cursorPositions.remove(window);
        editingBubbles.remove(window);
        currentInput.remove(window);  // Add this line
    }

    // Utility methods
    private static void drawNewTabButton(float x, float y, UIState state) {
        // Draw new tab button background
        Color buttonBg = state.isDarkMode ?
                new Color(0.25f, 0.25f, 0.25f, 1.0f) :
                new Color(0.8f, 0.8f, 0.8f, 1.0f);
        drawRoundedRect(x, y, NEW_TAB_BUTTON_WIDTH, 26, 5.0f, buttonBg);

        // Draw '+' symbol
        float lineWidth = 2.0f;
        glColor4f(state.textColor.getRed() / 255f,
                state.textColor.getGreen() / 255f,
                state.textColor.getBlue() / 255f,
                state.textColor.getAlpha() / 255f);

        float centerX = x + NEW_TAB_BUTTON_WIDTH / 2;
        float centerY = y + 26 / 2;

        // Horizontal line
        drawRect(centerX - 26/4, centerY - lineWidth/2, 26/2, lineWidth, state.textColor);

        // Vertical line
        drawRect(centerX - lineWidth/2, centerY - 26/4, lineWidth, 26/2, state.textColor);
    }
}