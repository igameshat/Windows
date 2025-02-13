package com.examples;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KeywordProcessor {
    static Map<String, String> dynamicKeywords = new ConcurrentHashMap<>();

    public static String processKeywords(String text) {
        if (text == null || text.isEmpty()) return text;

        MinecraftClient client = MinecraftClient.getInstance(); // Refresh client instance
        if (client == null || client.player == null || client.world == null) return text;

        Vec3d pos = client.player.getPos();
        String processedText = text;
        // Create lowercase version for checking
        String lowerText = text.toLowerCase();

        // Position keywords
        String format = String.format("%.2f, %.2f, %.2f", pos.x, pos.y, pos.z);
        processedText = replaceIgnoreCase(processedText, "{pos}", format);
        processedText = replaceIgnoreCase(processedText, "{POS}", format);
        processedText = replaceIgnoreCase(processedText, "{x}", String.format("%.2f", pos.x));
        processedText = replaceIgnoreCase(processedText, "{y}", String.format("%.2f", pos.y));
        processedText = replaceIgnoreCase(processedText, "{z}", String.format("%.2f", pos.z));
        processedText = replaceIgnoreCase(processedText, "{X}", String.format("%.2f", pos.x));
        processedText = replaceIgnoreCase(processedText, "{Y}", String.format("%.2f", pos.y));
        processedText = replaceIgnoreCase(processedText, "{Z}", String.format("%.2f", pos.z));

        // Item keyword
        if (lowerText.contains("{item}")) {
            ItemStack heldItem = client.player.getMainHandStack();
            processedText = replaceIgnoreCase(processedText, "{Item}",
                    heldItem.isEmpty() ? "air" : heldItem.getName().getString());
        }

        // Player list keyword
        if (lowerText.contains("{list}")) {
            List<String> playerNames = client.world.getPlayers().stream()
                    .map(PlayerEntity::getName)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            processedText = replaceIgnoreCase(processedText, "{list}", String.join(", ", playerNames));
        }

        // Biome
        if (lowerText.contains("{biome}")) {
            processedText = replaceIgnoreCase(processedText, "{biome}",
                    client.world.getBiome(client.player.getBlockPos()).getKey()
                            .map(key -> key.getValue().toString())
                            .orElse("unknown"));
        }

        if (lowerText.contains("cheese") || lowerText.contains(":cheese:")) {
            processedText = replaceIgnoreCase(processedText, "CHEESE!", "i like cheese");
            processedText = replaceIgnoreCase(processedText, "cheese!", "i like cheese");
        }

        // Dimension
        if (lowerText.contains("{dimension}") || lowerText.contains("{dim}")) {
            RegistryKey<World> dimension = client.world.getRegistryKey();
            processedText = replaceIgnoreCase(processedText, "{dimension}",
                    dimension.getValue().toString());
        }

        // Game time
        if (lowerText.contains("{gametime}")) {
            processedText = replaceIgnoreCase(processedText, "{gametime}",
                    String.valueOf(client.world.getTime()));
        }

        // Real time
        if (lowerText.contains("{time}")) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            processedText = replaceIgnoreCase(processedText, "{time}", now.format(formatter));
        }

        // Weather
        if (lowerText.contains("{weather}")) {
            String weather = client.world.isRaining()
                    ? (client.world.isThundering() ? "thundering" : "raining")
                    : "clear";
            processedText = replaceIgnoreCase(processedText, "{weather}", weather);
        }

        // Temperature
        if (lowerText.contains("{temp}")) {
            float temp = client.world.getBiome(client.player.getBlockPos()).value().getTemperature();
            processedText = replaceIgnoreCase(processedText, "{temp}", String.format("%.1f", temp));
        }

        // Moon phase and level
        if (lowerText.contains("{moonphase}") || lowerText.contains("{moonlevel}") || lowerText.contains("{moon}")) {
            int phase = client.world.getMoonPhase() % 8;
            String phaseName = switch (phase) {
                case 0 -> "Full Moon";
                case 1 -> "Waning Gibbous";
                case 2 -> "Last Quarter";
                case 3 -> "Waning Crescent";
                case 4 -> "New Moon";
                case 5 -> "Waxing Crescent";
                case 6 -> "First Quarter";
                case 7 -> "Waxing Gibbous";
                default -> "Unknown";
            };
            processedText = replaceIgnoreCase(processedText, "{moonphase}", phaseName);
            processedText = replaceIgnoreCase(processedText, "{moonlevel}", String.valueOf(phase));
            processedText = replaceIgnoreCase(processedText, "{moon}", phaseName + " " + phase);
        }

        // Game day
        if (lowerText.contains("{gameday}")) {
            long day = client.world.getTime() / 24000L;
            processedText = replaceIgnoreCase(processedText, "{gameday}", String.valueOf(day));
        }

        // Dynamic keywords
        for (Map.Entry<String, String> entry : dynamicKeywords.entrySet()) {
            processedText = replaceIgnoreCase(processedText, entry.getKey(), entry.getValue());
        }

        return processedText;
    }

    // Helper method to replace text while ignoring case
    private static String replaceIgnoreCase(String source, String target, String replacement) {
        if (source == null || target == null || replacement == null) {
            return source;
        }

        // If the target is empty, return the original string
        if (target.isEmpty()) {
            return source;
        }

        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();

        while (true) {
            int index = lowerSource.indexOf(lowerTarget, lastIndex);
            if (index == -1) {
                // No more occurrences found
                result.append(source.substring(lastIndex));
                break;
            }

            // Add the text before the match
            result.append(source, lastIndex, index);
            // Add the replacement
            result.append(replacement);

            lastIndex = index + target.length();
        }

        return result.toString();
    }

    public static String[] getAvailableKeywords() {
        String[] staticKeywords = {
                "{POS} - Shows current coordinates",
                "{x} - Shows X coordinate",
                "{y} - Shows Y coordinate",
                "{z} - Shows Z coordinate",
                "{Item} - Shows held item info",
                "{list} - Shows online players",
                "{biome} - Shows current biome",
                "{dimension} - Shows current dimension",
                "{gametime} - Shows in-game time",
                "{time} - Shows IRL time",
                "{weather} - Shows current weather",
                "{temp} - Shows biome temperature",
                "{moonphase} - Shows current moon phase",
                "{moonlevel} - Shows moon phase as number (0-7)",
                "{gameday} - Shows current in-game day",
                "{moon} - displays the current moon phase and moon phase as number (0-7)"
        };
        String[] dynamicKeywordsList = dynamicKeywords.entrySet().stream()
                .map(entry -> entry.getKey() + " - " + entry.getValue())
                .toArray(String[]::new);

        return Stream.concat(Arrays.stream(staticKeywords), Arrays.stream(dynamicKeywordsList))
                .toArray(String[]::new);
    }
}