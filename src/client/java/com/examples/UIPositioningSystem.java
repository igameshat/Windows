package com.examples;

import java.awt.*;
import java.util.*;
import java.util.List;

public class UIPositioningSystem {

    private final Rectangle windowBounds;
    private final List<Rectangle> activeBubbles;
    private final Map<String, Point> preferredPositions;

    public UIPositioningSystem(int windowWidth, int windowHeight) {
        this.windowBounds = new Rectangle(0, 0, windowWidth, windowHeight);
        this.activeBubbles = new ArrayList<>();
        this.preferredPositions = new HashMap<>();
    }

    public void updateWindowSize(int width, int height) {
        this.windowBounds.width = width;
        this.windowBounds.height = height;
        repositionAllBubbles();
    }

    public Point calculateBubblePosition(String bubbleId, int bubbleWidth, int bubbleHeight) {
        Rectangle newBubble = new Rectangle(0, 0, bubbleWidth, bubbleHeight);

        // Check if there's a preferred position
        if (preferredPositions.containsKey(bubbleId)) {
            Point preferred = preferredPositions.get(bubbleId);
            if (isValidPosition(new Rectangle(preferred.x, preferred.y, bubbleWidth, bubbleHeight))) {
                return preferred;
            }
        }

        // Find the best position using a grid-based approach
        int gridSize = Math.min(bubbleWidth, bubbleHeight) / 2;
        Point bestPosition = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = 0; x <= windowBounds.width - bubbleWidth; x += gridSize) {
            for (int y = 0; y <= windowBounds.height - bubbleHeight; y += gridSize) {
                newBubble.setLocation(x, y);
                if (isValidPosition(newBubble)) {
                    double score = calculatePositionScore(newBubble);
                    if (score < bestScore) {
                        bestScore = score;
                        bestPosition = new Point(x, y);
                    }
                }
            }
        }

        // If no position found, try to overlap with minimal overlap
        if (bestPosition == null) {
            bestPosition = findPositionWithMinimalOverlap(bubbleWidth, bubbleHeight);
        }

        return bestPosition;
    }

    private boolean isValidPosition(Rectangle bubble) {
        // Check window bounds
        if (!windowBounds.contains(bubble)) {
            return false;
        }

        // Check overlap with other bubbles
        for (Rectangle existing : activeBubbles) {
            if (bubble.intersects(existing)) {
                return false;
            }
        }

        return true;
    }

    private double calculatePositionScore(Rectangle bubble) {
        double score = 0;

        // Distance from center
        Point center = new Point(windowBounds.width / 2, windowBounds.height / 2);
        score += Point.distance(bubble.getCenterX(), bubble.getCenterY(),
                center.x, center.y);

        // Distance from other bubbles
        for (Rectangle existing : activeBubbles) {
            double distance = Point.distance(bubble.getCenterX(), bubble.getCenterY(),
                    existing.getCenterX(), existing.getCenterY());
            score -= Math.max(0, 300 - distance); // Prefer some spacing
        }

        return score;
    }

    private Point findPositionWithMinimalOverlap(int bubbleWidth, int bubbleHeight) {
        Point position = new Point(
                Math.max(0, Math.min(windowBounds.width - bubbleWidth, windowBounds.width / 2 - bubbleWidth / 2)),
                Math.max(0, Math.min(windowBounds.height - bubbleHeight, windowBounds.height / 2 - bubbleHeight / 2))
        );

        // Apply small offset for each existing bubble to create a cascade effect
        int offsetX = activeBubbles.size() * 20;
        int offsetY = activeBubbles.size() * 20;

        position.x = Math.max(0, Math.min(windowBounds.width - bubbleWidth, position.x + offsetX));
        position.y = Math.max(0, Math.min(windowBounds.height - bubbleHeight, position.y + offsetY));

        return position;
    }

    public void addBubble(String bubbleId, Rectangle bounds) {
        activeBubbles.add(bounds);
        preferredPositions.put(bubbleId, new Point(bounds.x, bounds.y));
    }

    public void removeBubble(String bubbleId, Rectangle bounds) {
        activeBubbles.remove(bounds);
        preferredPositions.remove(bubbleId);
    }

    public void setPreferredPosition(String bubbleId, Point position) {
        preferredPositions.put(bubbleId, position);
    }

    private void repositionAllBubbles() {
        List<Rectangle> oldPositions = new ArrayList<>(activeBubbles);
        activeBubbles.clear();

        for (Rectangle bubble : oldPositions) {
            Point newPosition = calculateBubblePosition(
                    bubble.toString(),
                    bubble.width,
                    bubble.height
            );
            bubble.setLocation(newPosition);
            activeBubbles.add(bubble);
        }
    }

    public void updateBubbleSize(String bubbleId, Rectangle oldBounds, int newWidth, int newHeight) {
        activeBubbles.remove(oldBounds);
        Point position = calculateBubblePosition(bubbleId, newWidth, newHeight);
        Rectangle newBounds = new Rectangle(position.x, position.y, newWidth, newHeight);
        activeBubbles.add(newBounds);
        preferredPositions.put(bubbleId, position);
    }
}