package com.examples;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AnimationConfig {
    private static final File CONFIG_FILE = new File(
            FabricLoader.getInstance().getConfigDir().toFile(),
            "windows_client_animations.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final float DEFAULT_DURATION = 0.5f;
    private static final Map<Long, Map<String, Animation>> windowAnimations = new ConcurrentHashMap<>();

    public static class Animation {
        float progress;
        long startTime;
        AnimationType type;
        float duration;

        public Animation(AnimationType type) {
            this.progress = 0.0f;
            this.startTime = System.currentTimeMillis();
            this.type = type;
            this.duration = DEFAULT_DURATION;
        }

        public float getProgress() {
            float elapsed = (System.currentTimeMillis() - startTime) / 1000.0f;
            progress = Math.min(1.0f, elapsed / duration);
            return type.interpolate(progress);
        }
    }

    public enum AnimationType {
        FADE_IN {
            float interpolate(float t) {
                return (float) (1 - Math.pow(1 - t, 3));
            }
        },
        SLIDE_IN {
            float interpolate(float t) {
                return 1 - (1 - t) * (1 - t);
            }
        };

        abstract float interpolate(float t);
    }

    public static void startAnimation(long window, String id, AnimationType type) {
        windowAnimations
                .computeIfAbsent(window, k -> new ConcurrentHashMap<>())
                .put(id, new Animation(type));
    }

    public static float getAnimationProgress(long window, String id) {
        Map<String, Animation> animations = windowAnimations.get(window);
        if (animations != null && animations.containsKey(id)) {
            Animation anim = animations.get(id);
            float progress = anim.getProgress();
            if (progress >= 1.0f) {
                animations.remove(id);
            }
            return progress;
        }
        return 1.0f;
    }

    public static void clearWindowAnimations(long window) {
        windowAnimations.remove(window);
    }
}