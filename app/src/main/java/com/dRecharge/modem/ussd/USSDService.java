package com.dRecharge.modem.ussd;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * AccessibilityService that automates USSD dialog interaction.
 *
 * KEY DESIGN RULE: AccessibilityEvent objects are RECYCLED by Android after
 * onAccessibilityEvent() returns. They must NEVER be stored in static or
 * instance fields. All event-based work happens synchronously inside the
 * callback. send() and cancel() use getRootInActiveWindow() instead.
 */
public class USSDService extends AccessibilityService {
    private static final int MAX_NODE_DEPTH = 20;

    private static USSDService serviceInstance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            USSDController controller = USSDController.instance;

            if (event == null || controller == null || !controller.isRunning) {
                return;
            }

            if (isActionButtonEvent(event)) {
                return;
            }

            if (isProgressMessage(getEventText(event)) || isMmiDialog(event)) {
                return;
            }

            if (LoginView(event) && notInputText(event)) {
                clickOnButton(event, 0);
                controller.stopRunning();
                String message = getEventText(event);
                if (!message.isEmpty()) {
                    USSDController.CallbackInvoke invokeCallback = controller.consumeCallbackInvoke();
                    if (invokeCallback != null) {
                        invokeCallback.over(message);
                    }
                }
            } else if (problemView(event) || LoginView(event)) {
                clickOnButton(event, 1);
                String message = getEventText(event);
                if (!message.isEmpty()) {
                    USSDController.CallbackInvoke invokeCallback = controller.consumeCallbackInvoke();
                    if (invokeCallback != null) {
                        invokeCallback.over(message);
                    }
                }
            } else if (isUSSDWidget(event)) {
                String response = getEventText(event);

                if (isPromptForInput(response)) {
                    USSDController.CallbackInvoke invokeCallback = controller.consumeCallbackInvoke();
                    if (invokeCallback != null) {
                        invokeCallback.responseInvoke(response);
                    } else {
                        USSDController.CallbackMessage messageCallback = controller.consumeCallbackMessage();
                        if (messageCallback != null) {
                            messageCallback.responseMessage(response);
                        }
                    }
                } else if (notInputText(event)) {
                    clickOnButton(event, 0);
                    controller.stopRunning();
                    USSDController.CallbackInvoke invokeCallback = controller.consumeCallbackInvoke();
                    if (invokeCallback != null) {
                        invokeCallback.responseInvoke(response);
                    } else {
                        USSDController.CallbackMessage messageCallback = controller.consumeCallbackMessage();
                        if (messageCallback != null) {
                            messageCallback.responseMessage(response);
                        }
                    }
                } else {
                    USSDController.CallbackInvoke invokeCallback = controller.consumeCallbackInvoke();
                    if (invokeCallback != null) {
                        invokeCallback.responseInvoke(response);
                    } else {
                        USSDController.CallbackMessage messageCallback = controller.consumeCallbackMessage();
                        if (messageCallback != null) {
                            messageCallback.responseMessage(response);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Swallow to prevent system from detecting misbehaviour and disabling the service
        }
    }

    /**
     * Send text into the active USSD dialog input field.
     * Uses getRootInActiveWindow() — never touches a cached/recycled event.
     */
    public static void send(String text) {
        if (serviceInstance == null) return;
        try {
            AccessibilityNodeInfo root = serviceInstance.getRootInActiveWindow();
            if (root == null) return;
            boolean set = setTextIntoNode(root, text);
            if (!set) return;
            boolean clicked = clickOnButtonByText(root, "Send")
                    || clickOnButtonByText(root, "SEND")
                    || clickOnButtonByText(root, "Next")
                    || clickOnButtonByText(root, "NEXT");
            if (!clicked) {
                clickOnButton(root, 1);
            }
        } catch (Exception ignored) {
            // Node may have been recycled by the system between calls.
        }
    }

    /**
     * Cancel the active USSD dialog.
     * Uses getRootInActiveWindow() — never touches a cached/recycled event.
     */
    public static void cancel() {
        if (serviceInstance == null) return;
        try {
            AccessibilityNodeInfo root = serviceInstance.getRootInActiveWindow();
            if (root == null) return;
            if (!clickOnButtonByText(root, "Cancel")
                    && !clickOnButtonByText(root, "CANCEL")
                    && !clickOnButtonByText(root, "OK")
                    && !clickOnButtonByText(root, "Ok")) {
                clickOnButton(root, 0);
            }
        } catch (Exception ignored) {
            // Node may have been recycled by the system between calls.
        }
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private static String getEventText(AccessibilityEvent event) {
        if (event == null) return "";
        try {
            List<CharSequence> texts = event.getText();
            if (texts != null && !texts.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (CharSequence text : texts) {
                    if (text != null && text.length() > 0) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(text);
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
            AccessibilityNodeInfo source = event.getSource();
            if (source == null) return "";
            // Wrap in try-catch: the source node can be recycled by the system
            // between getSource() and the recursive text collection below.
            try {
                return collectNodeText(source, 0).trim();
            } catch (IllegalStateException ignored) {
                // Node was recycled before we could read it.
                return "";
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasEventText(AccessibilityEvent event) {
        return !getEventText(event).isEmpty();
    }

    /**
     * Recursively collects text from a node tree, with a depth limit to
     * prevent ANR on devices with deep view hierarchies (MIUI, Samsung).
     */
    private static String collectNodeText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_NODE_DEPTH) return "";
        StringBuilder sb = new StringBuilder();
        CharSequence text = node.getText();
        if (text != null) sb.append(text).append("\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) sb.append(collectNodeText(child, depth + 1));
        }
        return sb.toString();
    }

    // ── Widget detection ─────────────────────────────────────────────────────

    private boolean isUSSDWidget(AccessibilityEvent event) {
        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        String packageName = event.getPackageName() == null ? "" : event.getPackageName().toString();
        boolean isKnownPackage = packageName.contains("com.android.phone")
                || packageName.contains("com.google.android.dialer")
                || packageName.contains("com.android.server.telecom")
                || packageName.contains("com.samsung.android.dialer")
                || packageName.contains("com.miui")
                || packageName.contains("com.oppo")
                || packageName.contains("com.coloros")
                || packageName.contains("com.coloros.dialer")
                || packageName.contains("com.vivo.dialer")
                || packageName.contains("com.oneplus.dialer")
                || packageName.contains("com.realme.dialer")
                || packageName.contains("com.android.dialer");
        boolean isDialogClass = className.contains("AlertDialog")
                || className.contains("Dialog")
                || className.contains("PromptDialog")
                || className.toLowerCase().contains("ussd");
        boolean hasRelevantText = hasEventText(event);
        return (isKnownPackage && (isDialogClass || hasRelevantText))
                || (isDialogClass && hasRelevantText);
    }

    private boolean LoginView(AccessibilityEvent event) {
        String text = getEventText(event);
        if (!isUSSDWidget(event) || text.isEmpty()) return false;
        if (USSDController.instance == null || USSDController.instance.map == null) return false;
        HashSet<String> loginSet = USSDController.instance.map.get(USSDController.KEY_LOGIN);
        return loginSet != null && loginSet.contains(text);
    }

    protected boolean problemView(AccessibilityEvent event) {
        String text = getEventText(event);
        if (!isUSSDWidget(event) || text.isEmpty()) return false;
        if (USSDController.instance == null || USSDController.instance.map == null) return false;
        HashSet<String> errorSet = USSDController.instance.map.get(USSDController.KEY_ERROR);
        return errorSet != null && errorSet.contains(text);
    }

    private static boolean isProgressMessage(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("ussd code running")
                || lower.contains("running")
                || lower.contains("loading")
                || lower.contains("please wait")
                || lower.contains("phone services");
    }

    private static boolean isMmiDialog(AccessibilityEvent event) {
        if (event == null || event.getClassName() == null) return false;
        return event.getClassName().toString().contains("MMIDialogActivity");
    }

    private static boolean isActionButtonEvent(AccessibilityEvent event) {
        if (event == null || event.getClassName() == null) return false;
        String className = event.getClassName().toString();
        if (!className.contains("Button")) return false;
        String text = getEventText(event).toLowerCase();
        return text.equals("cancel") || text.equals("send")
                || text.equals("ok") || text.equals("next");
    }

    private static boolean isPromptForInput(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String lower = text.toLowerCase();
        return (lower.contains("enter") && (lower.contains("pin")
                || lower.contains("menu pin") || lower.contains("confirm")))
                || (lower.contains("enter") && lower.contains("amount"))
                || (lower.contains("enter") && (lower.contains("account")
                || lower.contains("customer") || lower.contains("number")
                || lower.contains("phone")));
    }

    // ── Input detection ───────────────────────────────────────────────────────

    protected static boolean notInputText(AccessibilityEvent event) {
        return !hasEditableField(event)
                && !hasEditableField(getRootNode())
                && !hasSendButton(event)
                && !hasActionButtonsFromText(event);
    }

    private static boolean hasEditableField(AccessibilityEvent event) {
        if (event == null) return false;
        return hasEditableField(event.getSource());
    }

    private static boolean hasEditableField(AccessibilityNodeInfo node) {
        return hasEditableField(node, 0);
    }

    private static boolean hasEditableField(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_NODE_DEPTH) return false;
        CharSequence className = node.getClassName();
        String classNameStr = className == null ? "" : className.toString();
        if (node.isEditable() || classNameStr.contains("EditText") || supportsSetText(node)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasEditableField(node.getChild(i), depth + 1)) return true;
        }
        return false;
    }

    private static boolean hasSendButton(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event == null ? null : event.getSource();
        if (hasButtonText(source, "Send") || hasButtonText(source, "SEND")
                || hasButtonText(source, "Next") || hasButtonText(source, "NEXT")) {
            return true;
        }
        AccessibilityNodeInfo root = getRootNode();
        return hasButtonText(root, "Send") || hasButtonText(root, "SEND")
                || hasButtonText(root, "Next") || hasButtonText(root, "NEXT");
    }

    private static boolean hasActionButtonsFromText(AccessibilityEvent event) {
        String text = getEventText(event);
        if (text.isEmpty()) {
            AccessibilityNodeInfo root = getRootNode();
            text = root == null ? "" : collectNodeText(root, 0).trim();
        }
        if (text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("send") || lower.contains("next") || lower.contains("cancel");
    }

    // ── Node interaction ──────────────────────────────────────────────────────

    private static boolean setTextIntoNode(AccessibilityNodeInfo source, String data) {
        USSDController ussdController = USSDController.instance;
        if (source == null) return false;
        AccessibilityNodeInfo editable = findEditableNode(source, 0);
        if (editable == null) editable = source.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (editable == null) return false;

        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
        boolean set = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        if (!set && ussdController != null) {
            ClipboardManager clipboardManager = (ClipboardManager)
                    ussdController.context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            editable.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        }
        return true;
    }

    protected static void clickOnButton(AccessibilityEvent event, int index) {
        AccessibilityNodeInfo source = event == null ? null : event.getSource();
        clickOnButtonAtIndex(source, index);
    }

    protected static void clickOnButton(AccessibilityNodeInfo node, int index) {
        clickOnButtonAtIndex(node, index);
    }

    private static void clickOnButtonAtIndex(AccessibilityNodeInfo node, int index) {
        int count = -1;
        for (AccessibilityNodeInfo leaf : getLeaves(node)) {
            if (isButtonNode(leaf)) {
                count++;
                if (count == index) {
                    leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
            }
        }
    }

    private static boolean clickOnButtonByText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null || text.trim().isEmpty()) return false;
        for (AccessibilityNodeInfo leaf : getLeaves(node)) {
            if (textMatches(leaf.getText(), text)
                    || textMatches(leaf.getContentDescription(), text)) {
                AccessibilityNodeInfo clickable = findClickable(leaf);
                if (clickable != null) {
                    return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
        return false;
    }

    private static boolean hasButtonText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null || text.trim().isEmpty()) return false;
        for (AccessibilityNodeInfo leaf : getLeaves(node)) {
            if (textMatches(leaf.getText(), text)
                    || textMatches(leaf.getContentDescription(), text)) {
                return findClickable(leaf) != null;
            }
        }
        return false;
    }

    // ── Tree helpers ──────────────────────────────────────────────────────────

    /**
     * Collects all leaf nodes up to a depth of 20 to prevent ANR on complex
     * view hierarchies found on MIUI, Samsung One UI, and OPPO ColorOS.
     */
    private static List<AccessibilityNodeInfo> getLeaves(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (node != null) collectLeaves(leaves, node, 0);
        return leaves;
    }

    private static void collectLeaves(List<AccessibilityNodeInfo> leaves,
                                      AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_NODE_DEPTH) return;
        if (node.getChildCount() == 0) {
            leaves.add(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectLeaves(leaves, child, depth + 1);
        }
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_NODE_DEPTH) return null;
        CharSequence className = node.getClassName();
        String classNameStr = className == null ? "" : className.toString();
        if (node.isEditable() || classNameStr.contains("EditText")) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findEditableNode(node.getChild(i), depth + 1);
            if (child != null) return child;
        }
        return null;
    }

    private static AccessibilityNodeInfo findClickable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        try {
            if (node.isClickable()) return node;
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null) {
                if (parent.isClickable()) return parent;
                parent = parent.getParent();
            }
        } catch (IllegalStateException ignored) {
            // Node (or one of its ancestors) was recycled by the system mid-traversal.
        }
        return null;
    }

    private static boolean isButtonNode(AccessibilityNodeInfo node) {
        if (node == null || node.getClassName() == null) return false;
        return node.getClassName().toString().toLowerCase().contains("button");
    }

    private static boolean textMatches(CharSequence actual, String expected) {
        if (actual == null) return false;
        return expected.equalsIgnoreCase(actual.toString().trim());
    }

    private static boolean supportsSetText(AccessibilityNodeInfo node) {
        if (node == null) return false;
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions == null) return false;
        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action != null && action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                return true;
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo getRootNode() {
        if (serviceInstance == null) return null;
        return serviceInstance.getRootInActiveWindow();
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    /**
     * Returns true if this service is currently connected and active.
     * This is the most reliable runtime indicator — it is only set after
     * onServiceConnected fires and cleared when onDestroy is called.
     */
    public static boolean isServiceConnected() {
        return serviceInstance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceInstance = this;
        // Only update event types/flags on the existing info object from the XML.
        // Creating a new AccessibilityServiceInfo() and calling setServiceInfo() with it
        // would wipe out canRetrieveWindowContent (and other XML-declared capabilities),
        // which can cause the system to invalidate the service on some devices.
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
                info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

                // CRITICAL: notificationTimeout MUST match the XML config (1000ms).
                // If omitted, the value defaults to 0 which differs from the XML
                // declaration. On MIUI, Samsung, and ColorOS this mismatch causes
                // the system to silently downgrade or disable the service.
                info.notificationTimeout = 1000;

                // Android 13+: request the shortcut warning dialog so accidental
                // volume-key combos show a confirmation instead of silently toggling
                // the service off.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK;
                }

                setServiceInfo(info);
            }
        } catch (Exception e) {
            // Swallow any exception — a crash here causes the OS to permanently
            // disable the accessibility service, requiring manual re-enablement.
        }
    }

    @Override
    public void onInterrupt() {
        // Required override — nothing to do
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceInstance == this) {
            serviceInstance = null;
        }
    }
}
