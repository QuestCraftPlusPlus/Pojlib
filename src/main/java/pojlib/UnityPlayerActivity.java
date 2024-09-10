package pojlib;

import static android.os.Build.VERSION.SDK_INT;

import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.sendMouseButton;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Activity;
import android.app.ActivityGroup;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import com.unity3d.player.IUnityPlayerLifecycleEvents;
import com.unity3d.player.UnityPlayer;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import fr.spse.gamepad_remapper.RemapperManager;
import fr.spse.gamepad_remapper.RemapperView;
import pojlib.input.AWTInputBridge;
import pojlib.input.EfficientAndroidLWJGLKeycode;
import pojlib.input.GrabListener;
import pojlib.input.LwjglGlfwKeycode;
import pojlib.input.gamepad.DefaultDataProvider;
import pojlib.input.gamepad.Gamepad;
import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.Logger;

public class UnityPlayerActivity extends ActivityGroup implements IUnityPlayerLifecycleEvents, GrabListener
{
    protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code
    public static volatile ClipboardManager GLOBAL_CLIPBOARD;
    private PowerManager.WakeLock wakeLock;
    private Gamepad mGamepad = null;

    private RemapperManager mInputManager;

    private boolean mLastGrabState = false;

    // Override this in your custom UnityPlayerActivity to tweak the command line arguments passed to the Unity Android Player
    // The command line arguments are passed as a string, separated by spaces
    // UnityPlayerActivity calls this from 'onCreate'
    // Supported: -force-gles20, -force-gles30, -force-gles31, -force-gles31aep, -force-gles32, -force-gles, -force-vulkan
    // See https://docs.unity3d.com/Manual/CommandLineArguments.html
    // @param cmdLine the current command line arguments, may be null
    // @return the modified command line string or null
    private String appendCommandLineArgument(String cmdLine, String arg) {
        if (arg == null || arg.isEmpty())
            return cmdLine;
        else if (cmdLine == null || cmdLine.isEmpty())
            return arg;
        else
            return cmdLine + " " + arg;
    }

    protected String updateUnityCommandLineArguments(String cmdLine)
    {
        return appendCommandLineArgument(cmdLine, "-androidChainedSignalHandlerBehavior=disabled");
    }

    // Setup activity layout
    @Override protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        String cmdLine = updateUnityCommandLineArguments(getIntent().getStringExtra("unity"));
        getIntent().putExtra("unity", cmdLine);

        mUnityPlayer = new UnityPlayer(this, this);
        setContentView(mUnityPlayer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mUnityPlayer.requestFocus();

        File jre = new File(this.getFilesDir() + "/runtimes/JRE-22");
        if (!jre.exists()) {
            FileUtil.unzipArchiveFromAsset(this, "JRE-22.zip", this.getFilesDir() + "/runtimes/JRE-22");
        }

        try {
            installLWJGL(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        updateWindowSize(this);
        GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        mInputManager = new RemapperManager(this, new RemapperView.Builder(null)
                .remapA(true)
                .remapB(true)
                .remapX(true)
                .remapY(true)

                .remapLeftJoystick(true)
                .remapRightJoystick(true)
                .remapStart(true)
                .remapSelect(true)
                .remapLeftShoulder(true)
                .remapRightShoulder(true)
                .remapLeftTrigger(true)
                .remapRightTrigger(true)
                .remapDpad(true));

        CallbackBridge.nativeSetUseInputStackQueue(true);
    }

    public static String installLWJGL(Activity activity) throws IOException {
        File lwjgl = new File(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");
        byte[] lwjglAsset = FileUtil.loadFromAssetToByte(activity, "lwjgl/lwjgl-glfw-classes.jar");

        if (!lwjgl.exists()) {
            Objects.requireNonNull(lwjgl.getParentFile()).mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), lwjglAsset);
        } else if (!FileUtil.matchingAssetFile(lwjgl, lwjglAsset)) {
            Objects.requireNonNull(lwjgl.getParentFile()).mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), lwjglAsset);
        }

        return lwjgl.getAbsolutePath();
    }

    public static DisplayMetrics getDisplayMetrics(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();

        if(activity.isInMultiWindowMode() || activity.isInPictureInPictureMode()){
            //For devices with free form/split screen, we need window size, not screen size.
            displayMetrics = activity.getResources().getDisplayMetrics();
        }else{
            if (SDK_INT >= Build.VERSION_CODES.R) {
                activity.getDisplay().getRealMetrics(displayMetrics);
            } else { // Removed the clause for devices with unofficial notch support, since it also ruins all devices with virtual nav bars before P
                activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
            }
        }
        currentDisplayMetrics = displayMetrics;
        return displayMetrics;
    }

    public static DisplayMetrics currentDisplayMetrics;

    public static void updateWindowSize(Activity activity) {
        currentDisplayMetrics = getDisplayMetrics(activity);

        CallbackBridge.physicalWidth = currentDisplayMetrics.widthPixels;
        CallbackBridge.physicalHeight = currentDisplayMetrics.heightPixels;
    }

    public static float dpToPx(float dp) {
        //Better hope for the currentDisplayMetrics to be good
        return dp * currentDisplayMetrics.density;
    }

    public static float pxToDp(float px){
        //Better hope for the currentDisplayMetrics to be good
        return px / currentDisplayMetrics.density;
    }

    public static void querySystemClipboard() {
        ClipData clipData = GLOBAL_CLIPBOARD.getPrimaryClip();
        if(clipData == null) {
            AWTInputBridge.nativeClipboardReceived(null, null);
            return;
        }
        ClipData.Item firstClipItem = clipData.getItemAt(0);
        //TODO: coerce to HTML if the clip item is styled
        CharSequence clipItemText = firstClipItem.getText();
        if(clipItemText == null) {
            AWTInputBridge.nativeClipboardReceived(null, null);
            return;
        }
        AWTInputBridge.nativeClipboardReceived(clipItemText.toString(), "plain");
    }

    public static void putClipboardData(String data, String mimeType) {
        ClipData clipData = null;
        switch(mimeType) {
            case "text/plain":
                clipData = ClipData.newPlainText("AWT Paste", data);
                break;
            case "text/html":
                clipData = ClipData.newHtmlText("AWT Paste", data, data);
        }
        if(clipData != null) GLOBAL_CLIPBOARD.setPrimaryClip(clipData);
    }

    private void createGamepad(InputDevice inputDevice) {
        mGamepad = new Gamepad(inputDevice, DefaultDataProvider.INSTANCE);
    }

    @SuppressLint("NewApi")
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        int mouseCursorIndex = -1;

        if(Gamepad.isGamepadEvent(event)){
            if(mGamepad == null) createGamepad(event.getDevice());

            mInputManager.handleMotionEventInput(this, event, mGamepad);
            return true;
        }

        for(int i = 0; i < event.getPointerCount(); i++) {
            if(event.getToolType(i) != MotionEvent.TOOL_TYPE_MOUSE && event.getToolType(i) != MotionEvent.TOOL_TYPE_STYLUS ) continue;
            // Mouse found
            mouseCursorIndex = i;
            break;
        }
        if(mouseCursorIndex == -1) return false; // we cant consoom that, theres no mice!

        // Make sure we grabbed the mouse if necessary
        updateGrabState(CallbackBridge.isGrabbing());

        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE:
                CallbackBridge.mouseX = (event.getX(mouseCursorIndex) * 100);
                CallbackBridge.mouseY = (event.getY(mouseCursorIndex) * 100);
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
                return true;
            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll((double) event.getAxisValue(MotionEvent.AXIS_HSCROLL), (double) event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return sendMouseButtonUnconverted(event.getActionButton(),true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return sendMouseButtonUnconverted(event.getActionButton(),false);
            default:
                return false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean handleEvent;
        if(!(handleEvent = processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    /** The event for keyboard/ gamepad button inputs */
    public boolean processKeyEvent(KeyEvent event) {
        // Logger.getInstance().appendToLog("KeyEvent " + event.toString());

        //Filtering useless events by order of probability
        int eventKeycode = event.getKeyCode();
        if(eventKeycode == KeyEvent.KEYCODE_UNKNOWN) return true;
        if(eventKeycode == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if(eventKeycode == KeyEvent.KEYCODE_VOLUME_UP) return false;
        if(event.getRepeatCount() != 0) return true;
        int action = event.getAction();
        if(action == KeyEvent.ACTION_MULTIPLE) return true;
        // Ignore the cancelled up events. They occur when the user switches layouts.
        // In accordance with https://developer.android.com/reference/android/view/KeyEvent#FLAG_CANCELED
        if(action == KeyEvent.ACTION_UP &&
                (event.getFlags() & KeyEvent.FLAG_CANCELED) != 0) return true;

        //Sometimes, key events comes from SOME keys of the software keyboard
        //Even weirder, is is unknown why a key or another is selected to trigger a keyEvent
        if((event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD){
            if(eventKeycode == KeyEvent.KEYCODE_ENTER) return true; //We already listen to it.
            dispatchKeyEvent(event);
            return true;
        }

        //Sometimes, key events may come from the mouse
        if(event.getDevice() != null
                && ( (event.getSource() & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
                ||   (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE)  ){

            if(eventKeycode == KeyEvent.KEYCODE_BACK){
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, event.getAction() == KeyEvent.ACTION_DOWN);
                return true;
            }
        }

        if(Gamepad.isGamepadEvent(event)){
            if(mGamepad == null) createGamepad(event.getDevice());

            mInputManager.handleKeyEventInput(this, event, mGamepad);
            return true;
        }

        int index = EfficientAndroidLWJGLKeycode.getIndexByKey(eventKeycode);
        if(EfficientAndroidLWJGLKeycode.containsIndex(index)) {
            EfficientAndroidLWJGLKeycode.execKey(event, index);
            return true;
        }

        // Some events will be generated an infinite number of times when no consumed
        return (event.getFlags() & KeyEvent.FLAG_FALLBACK) == KeyEvent.FLAG_FALLBACK;
    }

    /** Convert the mouse button, then send it
     * @return Whether the event was processed
     */
    public static boolean sendMouseButtonUnconverted(int button, boolean status) {
        int glfwButton = -256;
        switch (button) {
            case MotionEvent.BUTTON_PRIMARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
                break;
            case MotionEvent.BUTTON_TERTIARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
                break;
            case MotionEvent.BUTTON_SECONDARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
                break;
        }
        if(glfwButton == -256) return false;
        sendMouseButton(glfwButton, status);
        return true;
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        mUnityPlayer.post(()->updateGrabState(isGrabbing));
    }

    // private TouchEventProcessor pickEventProcessor(boolean isGrabbing) {
    //    return isGrabbing ? mIngameProcessor : mInGUIProcessor;
    // }

    private void updateGrabState(boolean isGrabbing) {
        // if(mLastGrabState != isGrabbing) {
        //     mCurrentTouchProcessor.cancelPendingActions();
        //     mCurrentTouchProcessor = pickEventProcessor(isGrabbing);
        //     mLastGrabState = isGrabbing;
        // }
    }

        @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    // When Unity player unloaded move task to background
    @Override public void onUnityPlayerUnloaded() {
        moveTaskToBack(true);
    }

    // Callback before Unity player process is killed
    @Override public void onUnityPlayerQuitted() {
    }

    @Override protected void onNewIntent(Intent intent)
    {
        // To support deep linking, we need to make sure that the client can get access to
        // the last sent intent. The clients access this through a JNI api that allows them
        // to get the intent set on launch. To update that after launch we have to manually
        // replace the intent with the one caught here.
        setIntent(intent);
        mUnityPlayer.newIntent(intent);
    }

    // Quit Unity
    @Override protected void onDestroy ()
    {
        mUnityPlayer.destroy();
        wakeLock.release();
        super.onDestroy();
    }

    // If the activity is in multi window mode or resizing the activity is allowed we will use
    // onStart/onStop (the visibility callbacks) to determine when to pause/resume.
    // Otherwise it will be done in onPause/onResume as Unity has done historically to preserve
    // existing behavior.
    @Override protected void onStop()
    {
        super.onStop();

        mUnityPlayer.pause();
    }

    @Override protected void onStart()
    {
        super.onStart();

        mUnityPlayer.resume();
    }

    // Pause Unity
    @Override protected void onPause()
    {
        super.onPause();

        mUnityPlayer.pause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();

        mUnityPlayer.resume();
    }

    // Low Memory Unity
    @Override public void onLowMemory()
    {
        super.onLowMemory();
        mUnityPlayer.lowMemory();
    }

    // Trim Memory Unity
    @Override public void onTrimMemory(int level)
    {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL)
        {
            mUnityPlayer.lowMemory();
        }
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        mUnityPlayer.windowFocusChanged(hasFocus);
    }

/*    // For some reason the multiple keyevent type is not supported by the ndk.
    // Force event injection by overriding dispatchKeyEvent().
    @Override public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE)
            return mUnityPlayer.injectEvent(event);
        return super.dispatchKeyEvent(event);
    }*/

    // Pass any events not handled by (unfocused) views straight to UnityPlayer
    @Override public boolean onKeyUp(int keyCode, KeyEvent event)     {
        return mUnityPlayer.injectEvent(event);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)   {
        return mUnityPlayer.injectEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer.injectEvent(event); }
    /*API12*/ public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer.injectEvent(event); }
}