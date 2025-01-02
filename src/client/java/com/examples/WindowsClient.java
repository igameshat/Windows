package com.examples;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WindowsClient implements ClientModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("WindowsClient");

    private static final List<String> windowList = new ArrayList<>();
    private static final int MAX_WINDOWS = 10000;
    private static final Map<String, CustomScreen> activeScreens = new HashMap<>();

    public static class CustomScreen extends Screen {
        private final String windowName;

        public CustomScreen(String windowName) {
            super(Text.literal(windowName));
            this.windowName = windowName;
        }

        @Override
        protected void init() {
            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("Close"),
                            button -> {
                                this.close();
                                windowList.remove(windowName);
                                activeScreens.remove(windowName);
                            })
                    .dimensions(this.width / 2 - 50, this.height / 2 - 10, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }
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
                                context.getSource().sendFeedback(Text.literal("No windows in the list."));
                            } else {
                                context.getSource().sendFeedback(Text.literal("Windows: \n" + String.join(", ", windowList)));
                            }
                            return 1;
                        }))
                .then(ClientCommandManager.literal("open")
                        .executes(context -> {
                            if (windowList.size() >= MAX_WINDOWS) {
                                context.getSource().sendError(Text.literal("Window list is full (max " + MAX_WINDOWS + ")."));
                                return 0;
                            }
                            String windowName = generateUniqueWindowName();
                            windowList.add(windowName);
                            openWindow(windowName);
                            context.getSource().sendFeedback(Text.literal("Added window: " + windowName));
                            LOGGER.debug("Opened window: {}", windowName);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("window_name", StringArgumentType.string())
                                .executes(context -> {
                                    if (windowList.size() >= MAX_WINDOWS) {
                                        context.getSource().sendError(Text.literal("Window list is full (max " + MAX_WINDOWS + ")."));
                                        return 0;
                                    }
                                    String windowName = StringArgumentType.getString(context, "window_name");
                                    if (windowList.contains(windowName)) {
                                        context.getSource().sendError(Text.literal("A window with that name already exists!"));
                                        return 0;
                                    }
                                    windowList.add(windowName);
                                    openWindow(windowName);
                                    context.getSource().sendFeedback(Text.literal("Added window: " + windowName));
                                    LOGGER.debug("Opened window: {}", windowName);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("close")
                        .executes(context -> {
                            if (!windowList.isEmpty()) {
                                String lastWindow = windowList.get(windowList.size() - 1);
                                windowList.remove(lastWindow);
                                closeWindow(lastWindow);
                                context.getSource().sendFeedback(Text.literal("Closed last window: " + lastWindow));
                                LOGGER.debug("Closed last window: {}", lastWindow);
                            } else {
                                context.getSource().sendError(Text.literal("No windows to close."));
                            }
                            return 1;
                        })
                        .then(ClientCommandManager.argument("window_name", StringArgumentType.string())
                                .suggests(WINDOW_SUGGESTION_PROVIDER)
                                .executes(context -> {
                                    String windowName = StringArgumentType.getString(context, "window_name");
                                    if (windowList.remove(windowName)) {
                                        closeWindow(windowName);
                                        context.getSource().sendFeedback(Text.literal("Closed window: " + windowName));
                                        LOGGER.debug("Closed window: {}", windowName);
                                        return 1;
                                    } else {
                                        context.getSource().sendError(Text.literal("Window '" + windowName + "' not found."));
                                        return 0;
                                    }
                                }))));
    }

    private static void openWindow(String windowName) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            CustomScreen screen = new CustomScreen(windowName);
            activeScreens.put(windowName, screen);
            client.setScreen(screen);
        });
    }

    private static void closeWindow(String windowName) {
        MinecraftClient.getInstance().execute(() -> {
            CustomScreen screen = activeScreens.remove(windowName);
            if (screen != null && MinecraftClient.getInstance().currentScreen == screen) {
                MinecraftClient.getInstance().setScreen(null);
            }
        });
    }

    private static String generateUniqueWindowName() {
        int counter = 1;
        String name;
        do {
            name = "MinecraftInstance-" + counter++;
        } while (windowList.contains(name));
        return name;
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(WindowsClient::registerCommands);
    }
}
