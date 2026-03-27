package com.dRecharge.modem.ussd;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class USSDService extends AccessibilityService {
    private static final String TAG = "====USSD_SERVICE_TAG___"+USSDService.class.getSimpleName();

    private static USSDService serviceInstance;
    private static AccessibilityEvent event;
    private static AccessibilityNodeInfo lastUssdNode;
    private static long lastUssdTimeMs;
    private static final long USSD_NODE_TTL_MS = 10000;

    /** auth eng! b@ppe
     * Catch widget by Accessibility, when is showing at mobile display
     *
     * @param event AccessibilityEvent
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        USSDService.event = event;

        Log.d(TAG, "onAccessibilityEvent");

        Log.d(TAG, String.format(
                "onAccessibilityEvent: [type] %s [class] %s [package] %s [time] %s [text] %s",
                event.getEventType(), event.getClassName(), event.getPackageName(),
                event.getEventTime(), event.getText()));

        if (USSDController.instance == null || !USSDController.instance.isRunning) {
            return;
        }
        Log.d(TAG, "isRunning=true, class=" + event.getClassName() + " pkg=" + event.getPackageName());
        if (isActionButtonEvent(event)) {
            // Ignore isolated button events (Cancel/Send/OK) to avoid mis-detecting USSD.
            return;
        }
        if (isProgressMessage(getEventText(event)) || isMmiDialog(event)) {
            // Keep waiting for the real USSD dialog.
            Log.d(TAG, "progress dialog, waiting...");
            return;
        }
        if (LoginView(event) && notInputText(event)) {
            // first view or logView, do nothing, pass / FIRST MESSAGE
            clickOnButton(event, 0);
            USSDController.instance.isRunning = false;
            String message = getEventText(event);
            if (USSDController.instance.callbackInvoke != null && !message.isEmpty()) {
                USSDController.instance.callbackInvoke.over(message);
            }
        } else if (problemView(event) || LoginView(event)) {
            // deal down

            clickOnButton(event, 1);
            String message = getEventText(event);
            if (USSDController.instance.callbackInvoke != null && !message.isEmpty()) {
                USSDController.instance.callbackInvoke.over(message);
            }
        } else if (isUSSDWidget(event)) {
            // ready for work
            String response = getEventText(event);
            Log.d(TAG, "USSD widget detected, text=" + response.replace("\n", " | "));
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                lastUssdNode = source;
                lastUssdTimeMs = System.currentTimeMillis();
                Log.d(TAG, "cached node: " + nodeSummary(source));
            }
//            if (response.contains("\n")) {
//                response = response.substring(response.indexOf('\n') + 1);
//                Log.d("DDM",response);
//            }
            // bKash/Nagad/Rocket: "Enter Menu PIN to confirm" বা "Enter PIN" থাকলে Cancel ক্লিক করবেন না - input চাইছে, send() PIN পাঠাবে
            if (isPromptForInput(response)) {
                Log.d(TAG, "prompt for input (PIN/Amount/Number) - waiting for send()");
                if (USSDController.instance.callbackInvoke != null) {
                    USSDController.instance.callbackInvoke.responseInvoke(response);
                    USSDController.instance.callbackInvoke = null;
                } else if (USSDController.instance.callbackMessage != null) {
                    USSDController.instance.callbackMessage.responseMessage(response);
                }
            } else if (notInputText(event)) {
                Log.d(TAG, "no input detected, hasSend=" + hasSendButton(event)
                        + " hasActionText=" + hasActionButtonsFromText(event));
                // not more input panels / LAST MESSAGE
                
                // sent 'OK' button (index 0 = OK, not Cancel - only when really last message)
                clickOnButton(event, 0);
                USSDController.instance.isRunning = false;
                //USSDController.instance.callbackInvoke.over(response);
                try {
                    USSDController.instance.callbackInvoke.responseInvoke(response);
                } catch (Exception e){

                }
                try{
                    USSDController.instance.callbackMessage.responseMessage(response);
                } catch (Exception e){

                }
                Log.d("===USSD_RESPONSE",response);
            } else {
                Log.d(TAG, "input detected, waiting for send()");
                // sent option 1
                if (USSDController.instance.callbackInvoke != null) {
                    USSDController.instance.callbackInvoke.responseInvoke(response);
                    USSDController.instance.callbackInvoke = null;
                } else if (USSDController.instance.callbackMessage != null) {
                    USSDController.instance.callbackMessage.responseMessage(response);
                } else {
                    Log.d(TAG, "input detected but callbacks are null");
                }
            }
        }

    }

    /**
     * Send whatever you want via USSD
     *
     * @param text any string
     */
    public static void send(String text) {
        if (event == null) {
            Log.d(TAG, "send(): event null");
            return;
        }
        AccessibilityNodeInfo target = resolveActionNode(getActiveUssdNode(event));
        if (target == null) {
            target = getRootNode();
        }
        if (target == null) {
            Log.d(TAG, "send(): target null");
            return;
        }
        Log.d(TAG, "send(): target=" + nodeSummary(target) + " text=" + (text != null ? text.length() + " chars" : "null"));
        boolean set = setTextIntoNode(target, text);
        if (!set) {
            AccessibilityNodeInfo root = getRootNode();
            if (root != null && root != target) {
                Log.d(TAG, "send(): retry setText on root");
                set = setTextIntoNode(root, text);
                if (set) {
                    target = root;
                }
            }
        }
        boolean clicked = clickOnButtonByText(target, "Send")
                || clickOnButtonByText(target, "SEND")
                || clickOnButtonByText(target, "Next")
                || clickOnButtonByText(target, "NEXT");
        if (!clicked) {
            clickOnButton(target, 1);
        }
    }

    /**
     * Cancel USSD
     */
    public static void cancel() {
        if (event == null) {
            return;
        }
        AccessibilityNodeInfo target = resolveActionNode(getActiveUssdNode(event));
        if (target == null) {
            Log.d(TAG, "cancel(): target null");
            return;
        }
        Log.d(TAG, "cancel(): target=" + nodeSummary(target));
        if (!clickOnButtonByText(target, "Cancel")
                && !clickOnButtonByText(target, "CANCEL")
                && !clickOnButtonByText(target, "OK")
                && !clickOnButtonByText(target, "Ok")) {
            clickOnButton(target, 0);
        }
    }

    /**
     * set text into input text at USSD widget
     *
     * @param event AccessibilityEvent
     * @param data  Any String
     */
    private static boolean setTextIntoNode(AccessibilityNodeInfo source, String data) {
        USSDController ussdController = USSDController.instance;
        if (source == null) {
            return false;
        }
        AccessibilityNodeInfo editable = findEditableNode(source);
        if (editable == null) {
            editable = source.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        }
        if (editable == null) {
            Log.d(TAG, "setTextIntoNode(): no editable node");
            return false;
        }
        Log.d(TAG, "setTextIntoNode(): editable=" + nodeSummary(editable));
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
        boolean set = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        if (!set) {
            ClipboardManager clipboardManager = ((ClipboardManager) ussdController.context
                    .getSystemService(Context.CLIPBOARD_SERVICE));
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            editable.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        }
        return true;
    }

    /**
     * Method evaluate if USSD widget has input text
     *
     * @param event AccessibilityEvent
     * @return boolean has or not input text
     */
    protected static boolean notInputText(AccessibilityEvent event) {
        return !hasEditableField(event)
                && !hasEditableField(getRootNode())
                && !hasSendButton(event)
                && !hasActionButtonsFromText(event);
    }

    /**
     * The AccessibilityEvent is instance of USSD Widget class
     *
     * @param event AccessibilityEvent
     * @return boolean AccessibilityEvent is USSD
     */
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

    /**
     * The View has a login message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has login message
     */
    private boolean LoginView(AccessibilityEvent event) {
        String text = getEventText(event);
        return isUSSDWidget(event)
                && !text.isEmpty()
                && USSDController.instance.map.get(USSDController.KEY_LOGIN)
                .contains(text);
    }

    /**
     * The View has a problem message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has problem message
     */
    protected boolean problemView(AccessibilityEvent event) {
        String text = getEventText(event);
        return isUSSDWidget(event)
                && !text.isEmpty()
                && USSDController.instance.map.get(USSDController.KEY_ERROR)
                .contains(text);
    }

    private static String getEventText(AccessibilityEvent event) {
        if (event == null) {
            return "";
        }
        List<CharSequence> texts = event.getText();
        if (texts != null && !texts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence text : texts) {
                if (text != null && text.length() > 0) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        AccessibilityNodeInfo source = event.getSource();
        return source == null ? "" : collectNodeText(source).trim();
    }

    private boolean hasEventText(AccessibilityEvent event) {
        return !getEventText(event).isEmpty();
    }

    private static String collectNodeText(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        CharSequence text = node.getText();
        if (text != null) {
            sb.append(text).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(collectNodeText(node.getChild(i)));
        }
        return sb.toString();
    }

    /**
     * click a button using the index
     *
     * @param event AccessibilityEvent
     * @param index button's index
     */
    protected static void clickOnButton(AccessibilityEvent event, int index) {
        int count = -1;
        AccessibilityNodeInfo source = event == null ? null : event.getSource();
        for (AccessibilityNodeInfo leaf : getLeaves(source)) {
            if (leaf.getClassName().toString().toLowerCase().contains("button")) {
                count++;
                if (count == index) {
                    leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
    }

    protected static void clickOnButton(AccessibilityNodeInfo node, int index) {
        int count = -1;
        for (AccessibilityNodeInfo leaf : getLeaves(node)) {
            if (leaf.getClassName().toString().toLowerCase().contains("button")) {
                count++;
                if (count == index) {
                    leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
    }

    private static List<AccessibilityNodeInfo> getLeaves(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (node != null) {
            getLeaves(leaves, node);
        }

        return leaves;
    }

    private static void getLeaves(List<AccessibilityNodeInfo> leaves, AccessibilityNodeInfo node) {
        if (node.getChildCount() == 0) {
            leaves.add(node);
            return;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            getLeaves(leaves, node.getChild(i));
        }
    }

    private static boolean hasEditableField(AccessibilityEvent event) {
        if (event == null) {
            return false;
        }
        AccessibilityNodeInfo source = event.getSource();
        return hasEditableField(source);
    }

    private static boolean hasEditableField(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String classNameStr = className == null ? "" : className.toString();
        if (node.isEditable() || classNameStr.contains("EditText") || supportsSetText(node)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasEditableField(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean clickOnButtonByText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        for (AccessibilityNodeInfo leaf : getLeaves(node)) {
            if (textMatches(leaf.getText(), text) || textMatches(leaf.getContentDescription(), text)) {
                AccessibilityNodeInfo clickable = findClickable(leaf);
                if (clickable != null) {
                    return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        CharSequence className = node.getClassName();
        String classNameStr = className == null ? "" : className.toString();
        if (node.isEditable() || classNameStr.contains("EditText")) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findEditableNode(node.getChild(i));
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private static boolean textMatches(CharSequence actual, String expected) {
        if (actual == null) {
            return false;
        }
        return expected.equalsIgnoreCase(actual.toString().trim());
    }

    private static boolean supportsSetText(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action != null && action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSendButton(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event == null ? null : event.getSource();
        if (hasButtonText(source, "Send")
                || hasButtonText(source, "SEND")
                || hasButtonText(source, "Next")
                || hasButtonText(source, "NEXT")) {
            return true;
        }
        return hasButtonText(getRootNode(), "Send")
                || hasButtonText(getRootNode(), "SEND")
                || hasButtonText(getRootNode(), "Next")
                || hasButtonText(getRootNode(), "NEXT");
    }

    private static boolean hasActionButtonsFromText(AccessibilityEvent event) {
        String text = getEventText(event);
        if (text.isEmpty()) {
            AccessibilityNodeInfo root = getRootNode();
            text = root == null ? "" : collectNodeText(root).trim();
        }
        if (text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("send") || lower.contains("next") || lower.contains("cancel");
    }

    /**
     * bKash/Nagad/Rocket: যখন dialog "Enter PIN" বা "Enter Amount" বা "Enter Account No" চায় - Cancel ক্লিক করবেন না
     * Returns true if the dialog is asking for user input (PIN, amount, number etc) - app will send() next
     */
    private static boolean isPromptForInput(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return (lower.contains("enter") && (lower.contains("pin") || lower.contains("menu pin") || lower.contains("confirm")))
                || (lower.contains("enter") && lower.contains("amount"))
                || (lower.contains("enter") && (lower.contains("account") || lower.contains("customer") || lower.contains("number") || lower.contains("phone")));
    }

    private static boolean isProgressMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("ussd code running")
                || lower.contains("running")
                || lower.contains("loading")
                || lower.contains("please wait")
                || lower.contains("phone services");
    }

    private static boolean isMmiDialog(AccessibilityEvent event) {
        if (event == null || event.getClassName() == null) {
            return false;
        }
        return event.getClassName().toString().contains("MMIDialogActivity");
    }

    private static boolean isActionButtonEvent(AccessibilityEvent event) {
        if (event == null || event.getClassName() == null) {
            return false;
        }
        String className = event.getClassName().toString();
        if (!className.contains("Button")) {
            return false;
        }
        String text = getEventText(event).toLowerCase();
        return text.equals("cancel") || text.equals("send") || text.equals("ok") || text.equals("next");
    }

    private static boolean hasButtonText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        for (AccessibilityNodeInfo leaf : getLeaves(node)) {
            if (textMatches(leaf.getText(), text) || textMatches(leaf.getContentDescription(), text)) {
                return findClickable(leaf) != null;
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo findClickable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isClickable()) {
            return node;
        }
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                return parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static AccessibilityNodeInfo resolveActionNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return getRootNode();
        }
        if (!node.refresh()) {
            return getRootNode();
        }
        return node;
    }

    private static AccessibilityNodeInfo getActiveUssdNode(AccessibilityEvent currentEvent) {
        long now = System.currentTimeMillis();
        if (lastUssdNode != null && (now - lastUssdTimeMs) <= USSD_NODE_TTL_MS) {
            if (lastUssdNode.refresh()) {
                return lastUssdNode;
            }
        }
        if (currentEvent != null && currentEvent.getSource() != null) {
            return currentEvent.getSource();
        }
        return getRootNode();
    }

    private static AccessibilityNodeInfo getRootNode() {
        if (serviceInstance == null) {
            return null;
        }
        AccessibilityNodeInfo root = serviceInstance.getRootInActiveWindow();
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow is null");
        }
        return root;
    }

    private static String nodeSummary(AccessibilityNodeInfo node) {
        if (node == null) {
            return "null";
        }
        CharSequence cls = node.getClassName();
        CharSequence txt = node.getText();
        CharSequence desc = node.getContentDescription();
        return "class=" + (cls == null ? "" : cls)
                + " text=" + (txt == null ? "" : txt)
                + " desc=" + (desc == null ? "" : desc)
                + " clickable=" + node.isClickable()
                + " editable=" + node.isEditable();
    }

    /**
     * Active when SO interrupt the application
     */
    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    /**
     * Configure accessibility server from Android Operative System
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceInstance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        Log.d(TAG, "onServiceConnected");
    }
}
