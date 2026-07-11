package com.mahou.textbox;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class AccessibleActionLayer extends View {
    public interface ActionHandler {
        void onAction(String action);
    }

    private final ActionHandler handler;
    private final List<ActionNode> nodes = new ArrayList<>();
    private final Provider provider = new Provider();
    private int accessibilityFocusedId = View.NO_ID;

    public AccessibleActionLayer(Context context, ActionHandler handler) {
        super(context);
        this.handler = handler;
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(false);
        setClickable(false);
    }

    public void updateActions(String layoutJson) {
        try {
            JSONObject root = new JSONObject(layoutJson);
            double ratio = root.optDouble("pixelRatio", 1d);
            JSONArray actions = root.optJSONArray("actions");
            List<ActionNode> updated = new ArrayList<>();
            if (actions != null) {
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject item = actions.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String action = item.optString("action", "");
                    String label = item.optString("label", "");
                    if (action.isEmpty() || label.isEmpty()) {
                        continue;
                    }
                    Rect bounds = new Rect(
                            (int) Math.round(item.optDouble("left") * ratio),
                            (int) Math.round(item.optDouble("top") * ratio),
                            (int) Math.round(item.optDouble("right") * ratio),
                            (int) Math.round(item.optDouble("bottom") * ratio)
                    );
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        updated.add(new ActionNode(action, label, bounds, item.optBoolean("enabled", true)));
                    }
                }
            }
            nodes.clear();
            nodes.addAll(updated);
            invalidate();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        } catch (Exception ignored) {
            nodes.clear();
        }
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return provider;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private final class Provider extends AccessibilityNodeProvider {
        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            if (virtualViewId == View.NO_ID) {
                AccessibilityNodeInfo host = AccessibilityNodeInfo.obtain(AccessibleActionLayer.this);
                AccessibleActionLayer.this.onInitializeAccessibilityNodeInfo(host);
                for (int i = 0; i < nodes.size(); i++) {
                    host.addChild(AccessibleActionLayer.this, i);
                }
                return host;
            }
            if (virtualViewId < 0 || virtualViewId >= nodes.size()) {
                return null;
            }
            ActionNode node = nodes.get(virtualViewId);
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            info.setSource(AccessibleActionLayer.this, virtualViewId);
            info.setParent(AccessibleActionLayer.this);
            info.setPackageName(getContext().getPackageName());
            info.setClassName(android.widget.Button.class.getName());
            info.setText(node.label);
            info.setContentDescription(node.label);
            info.setEnabled(node.enabled);
            info.setClickable(node.enabled);
            info.setFocusable(true);
            info.setVisibleToUser(isShown());
            info.setBoundsInParent(node.bounds);
            int[] location = new int[2];
            getLocationOnScreen(location);
            Rect screen = new Rect(node.bounds);
            screen.offset(location[0], location[1]);
            info.setBoundsInScreen(screen);
            if (node.enabled) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            }
            if (accessibilityFocusedId == virtualViewId) {
                info.setAccessibilityFocused(true);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            } else {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            }
            return info;
        }

        @Override
        public boolean performAction(int virtualViewId, int action, Bundle arguments) {
            if (virtualViewId < 0 || virtualViewId >= nodes.size()) {
                return false;
            }
            ActionNode node = nodes.get(virtualViewId);
            if (action == AccessibilityNodeInfo.ACTION_CLICK && node.enabled) {
                handler.onAction(node.action);
                sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                accessibilityFocusedId = virtualViewId;
                invalidate();
                sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                accessibilityFocusedId = View.NO_ID;
                invalidate();
                sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                return true;
            }
            return false;
        }
    }

    private void sendVirtualEvent(int id, int eventType) {
        if (!isShown()) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(getContext().getPackageName());
        event.setClassName(android.widget.Button.class.getName());
        event.setSource(this, id);
        getParent().requestSendAccessibilityEvent(this, event);
    }

    private static final class ActionNode {
        final String action;
        final String label;
        final Rect bounds;
        final boolean enabled;

        ActionNode(String action, String label, Rect bounds, boolean enabled) {
            this.action = action;
            this.label = label;
            this.bounds = bounds;
            this.enabled = enabled;
        }
    }
}
