package com.examples;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

public class InputSystem {
    private static final Map<Long, InputState> windowInputStates = new ConcurrentHashMap<>();
    private static final Map<Long, Set<ClickZone>> clickZones = new ConcurrentHashMap<>();
    private static final Map<Long, Element> focusedElements = new ConcurrentHashMap<>();
    private static final Map<Long, List<InputEventListener>> eventListeners = new ConcurrentHashMap<>();
    private static final Map<Long, InputMode> inputModes = new ConcurrentHashMap<>();

    // Enhanced input state tracking
    static class InputState {
        boolean isMouseDown;
        boolean isDragging;
        double lastMouseX;
        double lastMouseY;
        Element draggedElement;
        DragOffset dragOffset;
        long lastClickTime;
        int clickCount;
        Set<Integer> pressedKeys = new HashSet<>();
        InputMode currentMode = InputMode.NORMAL;
        Element hoveredElement;
        Element lastClickedElement;
        boolean isShiftPressed;
        boolean isCtrlPressed;
        boolean isAltPressed;
    }

    enum InputMode {
        NORMAL,
        DRAGGING,
        TEXT_INPUT,
        MODAL
    }

    static class DragOffset {
        double x;
        double y;
        double startX;
        double startY;
        long startTime;

        DragOffset(double x, double y) {
            this.x = x;
            this.y = y;
            this.startX = x;
            this.startY = y;
            this.startTime = System.currentTimeMillis();
        }

        double getDragDistance() {
            return Math.sqrt(Math.pow(x - startX, 2) + Math.pow(y - startY, 2));
        }

        long getDragDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }

    static class ClickZone {
        Rectangle bounds;
        Element element;
        int zIndex;
        boolean isEnabled;
        Set<Integer> allowedButtons;
        Consumer<InputEvent> clickHandler;

        ClickZone(Rectangle bounds, Element element, int zIndex) {
            this.bounds = bounds;
            this.element = element;
            this.zIndex = zIndex;
            this.isEnabled = true;
            this.allowedButtons = new HashSet<>();
            this.allowedButtons.add(GLFW_MOUSE_BUTTON_LEFT);
        }

        boolean contains(double x, double y) {
            return isEnabled && bounds.contains(x, y);
        }

        void setClickHandler(Consumer<InputEvent> handler) {
            this.clickHandler = handler;
        }

        void addAllowedButton(int button) {
            allowedButtons.add(button);
        }
    }

    static class InputEvent {
        final EventType type;
        final double x;
        final double y;
        final int button;
        final Element source;
        final Element target;
        boolean consumed;

        InputEvent(EventType type, double x, double y, int button, Element source, Element target) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.button = button;
            this.source = source;
            this.target = target;
            this.consumed = false;
        }

        void consume() {
            this.consumed = true;
        }
    }

    enum EventType {
        MOUSE_CLICK,
        MOUSE_DOWN,
        MOUSE_UP,
        MOUSE_MOVE,
        DRAG_START,
        DRAG,
        DRAG_END,
        FOCUS_GAINED,
        FOCUS_LOST,
        KEY_PRESS,
        KEY_RELEASE
    }

    interface InputEventListener {
        void onInputEvent(InputEvent event);
    }

    interface Element {
        void onClick(double x, double y, int button);
        void onDragStart(double x, double y);
        void onDrag(double x, double y);
        void onDragEnd(double x, double y);
        void onFocusGained();
        void onFocusLost();
        void onHoverStart();
        void onHoverEnd();
        Rectangle getBounds();
        int getZIndex();
        boolean isEnabled();
        void setEnabled(boolean enabled);
    }

    // Enhanced window state management
    static void initializeWindow(long window) {
        windowInputStates.put(window, new InputState());
        clickZones.put(window, new HashSet<>());
        eventListeners.put(window, new ArrayList<>());
        inputModes.put(window, InputMode.NORMAL);
    }

    static void cleanupWindow(long window) {
        windowInputStates.remove(window);
        clickZones.remove(window);
        focusedElements.remove(window);
        eventListeners.remove(window);
        inputModes.remove(window);
    }

    // Enhanced click zone management
    static void registerClickZone(long window, ClickZone zone) {
        Set<ClickZone> zones = clickZones.computeIfAbsent(window, k -> new HashSet<>());
        zones.add(zone);
    }

    static void unregisterClickZone(long window, ClickZone zone) {
        Set<ClickZone> zones = clickZones.get(window);
        if (zones != null) {
            zones.remove(zone);
        }
    }

    // Enhanced focus management
    static void setFocus(long window, Element element) {
        Element currentFocus = focusedElements.get(window);
        if (currentFocus != element) {
            InputEvent focusLostEvent = null;
            InputEvent focusGainedEvent = null;

            if (currentFocus != null) {
                focusLostEvent = new InputEvent(EventType.FOCUS_LOST, 0, 0, 0, currentFocus, null);
                currentFocus.onFocusLost();
            }

            if (element != null) {
                focusGainedEvent = new InputEvent(EventType.FOCUS_GAINED, 0, 0, 0, null, element);
                element.onFocusGained();
            }

            focusedElements.put(window, element);

            // Propagate focus events
            if (focusLostEvent != null) {
                propagateEvent(window, focusLostEvent);
            }
            if (focusGainedEvent != null) {
                propagateEvent(window, focusGainedEvent);
            }
        }
    }

    // Enhanced event handling
    static void handleMouseButton(long window, int button, int action, int mods) {
        InputState state = windowInputStates.get(window);
        if (state == null) return;

        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);

        // Update modifier key states
        state.isShiftPressed = (mods & GLFW_MOD_SHIFT) != 0;
        state.isCtrlPressed = (mods & GLFW_MOD_CONTROL) != 0;
        state.isAltPressed = (mods & GLFW_MOD_ALT) != 0;

        if (action == GLFW_PRESS) {
            handleMousePress(window, state, xpos[0], ypos[0], button);
        } else if (action == GLFW_RELEASE) {
            handleMouseRelease(window, state, xpos[0], ypos[0], button);
        }
    }

    private static void handleMousePress(long window, InputState state, double x, double y, int button) {
        state.isMouseDown = true;
        state.lastMouseX = x;
        state.lastMouseY = y;

        // Handle multi-click detection
        long currentTime = System.currentTimeMillis();
        if (currentTime - state.lastClickTime < 500) {
            state.clickCount++;
        } else {
            state.clickCount = 1;
        }
        state.lastClickTime = currentTime;

        Element clickedElement = findTopElementAt(window, x, y);
        if (clickedElement != null && clickedElement.isEnabled()) {
            state.lastClickedElement = clickedElement;
            clickedElement.onClick(x, y, button);
            setFocus(window, clickedElement);

            InputEvent event = new InputEvent(EventType.MOUSE_DOWN, x, y, button, clickedElement, clickedElement);
            propagateEvent(window, event);
        } else {
            setFocus(window, null);
        }
    }

    private static void handleMouseRelease(long window, InputState state, double x, double y, int button) {
        if (state.isDragging && state.draggedElement != null) {
            state.draggedElement.onDragEnd(x, y);
            InputEvent dragEndEvent = new InputEvent(EventType.DRAG_END, x, y, button,
                    state.draggedElement, state.draggedElement);
            propagateEvent(window, dragEndEvent);
        }

        if (state.lastClickedElement != null) {
            InputEvent clickEvent = new InputEvent(EventType.MOUSE_CLICK, x, y, button,
                    state.lastClickedElement, state.lastClickedElement);
            propagateEvent(window, clickEvent);
        }

        state.isMouseDown = false;
        state.isDragging = false;
        state.draggedElement = null;
        state.dragOffset = null;
        state.lastClickedElement = null;
    }

    static void handleMouseMove(long window, double xpos, double ypos) {
        InputState state = windowInputStates.get(window);
        if (state == null) return;

        // Handle hover state changes
        Element elementUnderMouse = findTopElementAt(window, xpos, ypos);
        if (elementUnderMouse != state.hoveredElement) {
            if (state.hoveredElement != null) {
                state.hoveredElement.onHoverEnd();
            }
            if (elementUnderMouse != null) {
                elementUnderMouse.onHoverStart();
            }
            state.hoveredElement = elementUnderMouse;
        }

        if (state.isMouseDown) {
            handleDragging(window, state, xpos, ypos);
        }

        // Propagate mouse move event
        InputEvent moveEvent = new InputEvent(EventType.MOUSE_MOVE, xpos, ypos, 0,
                state.hoveredElement, state.hoveredElement);
        propagateEvent(window, moveEvent);
    }

    private static void handleDragging(long window, InputState state, double xpos, double ypos) {
        if (!state.isDragging &&
                (Math.abs(xpos - state.lastMouseX) > 3 || Math.abs(ypos - state.lastMouseY) > 3)) {
            // Start drag if mouse has moved more than 3 pixels
            state.isDragging = true;
            Element elementUnderMouse = findTopElementAt(window, state.lastMouseX, state.lastMouseY);
            if (elementUnderMouse != null && elementUnderMouse.isEnabled()) {
                state.draggedElement = elementUnderMouse;
                state.dragOffset = new DragOffset(
                        state.lastMouseX - elementUnderMouse.getBounds().getX(),
                        state.lastMouseY - elementUnderMouse.getBounds().getY()
                );
                elementUnderMouse.onDragStart(xpos, ypos);

                InputEvent dragStartEvent = new InputEvent(EventType.DRAG_START, xpos, ypos, 0,
                        elementUnderMouse, elementUnderMouse);
                propagateEvent(window, dragStartEvent);
            }
        }

        if (state.isDragging && state.draggedElement != null) {
            state.draggedElement.onDrag(xpos, ypos);
            InputEvent dragEvent = new InputEvent(EventType.DRAG, xpos, ypos, 0,
                    state.draggedElement, state.draggedElement);
            propagateEvent(window, dragEvent);
        }
    }

    // Event listener management
    static void addEventListener(long window, InputEventListener listener) {
        List<InputEventListener> listeners = eventListeners.computeIfAbsent(window, k -> new ArrayList<>());
        listeners.add(listener);
    }

    static void removeEventListener(long window, InputEventListener listener) {
        List<InputEventListener> listeners = eventListeners.get(window);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    private static void propagateEvent(long window, InputEvent event) {
        List<InputEventListener> listeners = eventListeners.get(window);
        if (listeners != null) {
            for (InputEventListener listener : listeners) {
                if (!event.consumed) {
                    listener.onInputEvent(event);
                }
            }
        }
    }

    private static Element findTopElementAt(long window, double x, double y) {
        Set<ClickZone> zones = clickZones.get(window);
        if (zones == null || zones.isEmpty()) return null;

        return zones.stream()
                .filter(zone -> zone.contains(x, y) && zone.element.isEnabled())
                .max(Comparator.comparingInt(zone -> zone.zIndex))
                .map(zone -> zone.element)
                .orElse(null);
    }

    // Input mode management
    static void setInputMode(long window, InputMode mode) {
        inputModes.put(window, mode);
    }

    static InputMode getInputMode(long window) {
        return inputModes.getOrDefault(window, InputMode.NORMAL);
    }
}