package com.examples;

import com.google.gson.*;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class SessionManager {
    private static final Path SESSION_DIR = Paths.get(
            WindowsClient.getWindowsClientConfigDir().getPath(),
            "sessions"
    );

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Color.class, new ColorAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static class WindowSession {
        public String sessionId;
        public String displayName;
        public LocalDateTime createdAt;
        public LocalDateTime lastModified;
        public List<UI.TabInfo> tabs;
        public Map<String, Object> metadata;
        public float windowScale;
        public boolean isDarkMode;

        public WindowSession() {
            this.sessionId = generateSessionId();
            this.createdAt = LocalDateTime.now();
            this.lastModified = LocalDateTime.now();
            this.metadata = new HashMap<>();
            this.tabs = new ArrayList<>();
        }
    }


    private static String generateSessionId() {
        return String.format("%s-%s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                UUID.randomUUID().toString().substring(0, 8)
        );
    }

    static {
        try {
            Files.createDirectories(SESSION_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create session directory: " + e.getMessage());
        }
    }

    public static void saveSession(long window, List<UI.TabInfo> tabs, String customName) {
        try {
            WindowSession session = new WindowSession();
            session.tabs = new ArrayList<>();

            // Deep copy the tabs to ensure all data is captured
            for (UI.TabInfo tab : tabs) {
                UI.TabInfo newTab = new UI.TabInfo(tab.name);
                newTab.color = tab.color;
                newTab.scrollOffset = tab.scrollOffset;

                for (UI.TextBubble bubble : tab.bubbles) {
                    UI.TextBubble newBubble = new UI.TextBubble(bubble.text, bubble.scale);
                    newBubble.x = bubble.x;
                    newBubble.y = bubble.y;
                    newBubble.timestamp = bubble.timestamp;
                    newBubble.colorR = bubble.colorR;
                    newBubble.colorG = bubble.colorG;
                    newBubble.colorB = bubble.colorB;
                    newBubble.colorA = bubble.colorA;
                    newBubble.style = bubble.style;
                    newBubble.isBold = bubble.isBold;
                    newBubble.isItalic = bubble.isItalic;
                    newTab.bubbles.add(newBubble);
                }
                session.tabs.add(newTab);
            }

            session.displayName = customName != null ? customName : "Session-" + session.sessionId;

            // Capture UI state
            UI.UIState uiState = UI.getCurrentState();
            if (uiState != null) {
                session.isDarkMode = uiState.isDarkMode;
                session.windowScale = uiState.scale;
            }

            String filename = sanitizeFileName(session.displayName) + ".json";
            Path sessionFile = SESSION_DIR.resolve(filename);

            System.out.println("Saving session to: " + sessionFile);
            String json = GSON.toJson(session);
            System.out.println("Session JSON: " + json);

            try (Writer writer = Files.newBufferedWriter(sessionFile)) {
                GSON.toJson(session, writer);
                System.out.println("Session saved successfully with " + session.tabs.size() + " tabs");
            }
        } catch (Exception e) {
            System.err.println("Error saving session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static WindowSession loadSession(String sessionName) {
        try {
            Path sessionFile = SESSION_DIR.resolve(sanitizeFileName(sessionName) + ".json");
            System.out.println("Loading session from: " + sessionFile);

            if (!Files.exists(sessionFile)) {
                System.err.println("Session file does not exist: " + sessionFile);
                return null;
            }

            try (Reader reader = Files.newBufferedReader(sessionFile)) {
                WindowSession session = GSON.fromJson(reader, WindowSession.class);
                if (session.tabs == null) session.tabs = new ArrayList<>();

                // Update last modified time
                session.lastModified = LocalDateTime.now();

                System.out.println("Session loaded with " + session.tabs.size() + " tabs");
                return session;
            }
        } catch (Exception e) {
            System.err.println("Error loading session: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDateTime.parse(json.getAsString());
        }
    }

    public static void renameSession(String oldName, String newName) {
        try {
            Path oldFile = SESSION_DIR.resolve(sanitizeFileName(oldName) + ".json");
            Path newFile = SESSION_DIR.resolve(sanitizeFileName(newName) + ".json");

            WindowSession session = loadSession(oldName);
            if (session != null) {
                session.displayName = newName;
                session.lastModified = LocalDateTime.now();

                try (Writer writer = Files.newBufferedWriter(newFile)) {
                    GSON.toJson(session, writer);
                }

                Files.deleteIfExists(oldFile);
                System.out.println("Session renamed from " + oldName + " to " + newName);
            }
        } catch (Exception e) {
            System.err.println("Error renaming session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void deleteSession(String sessionName) {
        try {
            Path sessionFile = SESSION_DIR.resolve(sanitizeFileName(sessionName) + ".json");
            Files.deleteIfExists(sessionFile);
            System.out.println("Session deleted: " + sessionName);
        } catch (Exception e) {
            System.err.println("Error deleting session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<String> listSessions() {
        try {
            if (!Files.exists(SESSION_DIR)) {
                Files.createDirectories(SESSION_DIR);
            }
            return Files.list(SESSION_DIR)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error listing sessions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static class SessionInfo {
        private String name;
        private LocalDateTime created;
        private LocalDateTime lastModified;
        private int tabCount;

        public SessionInfo(String name, LocalDateTime created, LocalDateTime lastModified, int tabCount) {
            this.name = name;
            this.created = created;
            this.lastModified = lastModified;
            this.tabCount = tabCount;
        }

        public String getName() { return name; }
        public LocalDateTime getCreated() { return created; }
        public LocalDateTime getLastModified() { return lastModified; }
        public int getTabCount() { return tabCount; }
    }

    public static List<SessionInfo> listSessionsWithInfo() {
        try {
            return Files.list(SESSION_DIR)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            WindowSession session = loadSession(getNameWithoutExtension(path));
                            return new SessionInfo(
                                    session.displayName,
                                    session.createdAt,
                                    session.lastModified,
                                    session.tabs.size()
                            );
                        } catch (Exception e) {
                            System.err.println("Error reading session file: " + path);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> b.getLastModified().compareTo(a.getLastModified()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error listing sessions with info: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private static String getNameWithoutExtension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    public static void validateSessions() {
        try {
            Files.list(SESSION_DIR)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            loadSession(getNameWithoutExtension(path));
                            System.out.println("Validated session: " + path);
                        } catch (Exception e) {
                            System.err.println("Invalid session file: " + path);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error validating sessions: " + e.getMessage());
        }
    }
}