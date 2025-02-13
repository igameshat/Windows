package com.examples;

import com.google.gson.*;
import java.awt.Color;
import java.lang.reflect.Type;

public class ColorAdapter implements JsonSerializer<Color>, JsonDeserializer<Color> {
    @Override
    public JsonElement serialize(Color src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getRGB());
    }

    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return new Color(json.getAsInt(), true);
    }
}