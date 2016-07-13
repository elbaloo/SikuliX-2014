package org.sikuli.android;

import org.sikuli.basics.Debug;
import org.sikuli.basics.Settings;
import org.sikuli.script.*;
import org.sikuli.util.*;
import se.vidstige.jadb.JadbException;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Törcsi on 2016. 06. 26.
 * Revised by RaiMan
 */
public class ADBScreen extends Region implements EventObserver, IScreen {

  private static String me = "ADBScreen: ";
  private static void log(int level, String message, Object... args) {
    Debug.logx(level, me + message, args);
  }

  private static boolean isFake = false;
  protected IRobot robot = null;
  private static int logLvl = 3;
  private ScreenImage lastScreenImage = null;
  private Rectangle bounds;

  private boolean waitPrompt = false;
  protected OverlayCapturePrompt prompt;
  private String promptMsg = "Select a region on the screen";
  private static int waitForScreenshot = 300;

  public boolean needsUnLock = true;
  public int waitAfterAction = 1;

  public static int MENU = 82;

  //---------------------------Inits
  private ADBDevice device = null;

  public ADBScreen() {
    super();
    setOtherScreen();

    device = ADBDevice.get();
    if (device != null) {
      robot = device.getRobot(this);
      robot.setAutoDelay(10);
      bounds = device.getBounds();
      w = bounds.width;
      h = bounds.height;
    }
  }

  public boolean isValid() {
    return null != device;
  }

  public ADBDevice getDevice() {
    return device;
  }

  public String toString() {
    if (null == device) {
      return "ADBScreen: No Android device attached";
    } else {
      return String.format("ADBScreen: Android device: %s", getDeviceDescription());
    }
  }

  public String getDeviceDescription() {
    return String.format("%s (%d x %d)", device.getDeviceSerial(), bounds.width, bounds.height);
  }

  public void wakeUp(int seconds) {
    if (null == device) {
      return;
    }
    if (!device.isDisplayOn()) {
      device.wakeUp(seconds);
      if (needsUnLock) {
        swipeUp();
        RunTime.pause(waitAfterAction);
      }
    }
  }

  public <PFRML> void tap(PFRML target) throws FindFailed {
    if (device == null) {
      return;
    }
    Location loc = getLocationFromTarget(target);
    if(loc != null) {
      device.tap(loc.x, loc.y);
      RunTime.pause(waitAfterAction);
    }
  }

  public void tapButton(int button) {
    device.inputKeyEvent(button);
  }

  public <PFRML> void swipe(PFRML from, PFRML to) throws FindFailed {
    if (device == null) {
      return;
    }
    Location locFrom = getLocationFromTarget(from);
    Location locTo = getLocationFromTarget(from);
    if(locFrom != null && locTo != null) {
      device.swipe(locFrom.x, locFrom.y, locTo.x, locTo.y);
      RunTime.pause(waitAfterAction);
    }
  }

  public void swipeUp() {
    if (device == null) {
      return;
    }
    int midX = (int) (w/2);
    int swipeStep = (int) (h/5);
    device.swipe(midX, h - swipeStep, midX, swipeStep);
    RunTime.pause(waitAfterAction);
  }

  public void swipeDown() {
    if (device == null) {
      return;
    }
    int midX = (int) (w/2);
    int swipeStep = (int) (h/5);
    device.swipe(midX, swipeStep, midX, h - swipeStep);
    RunTime.pause(waitAfterAction);
  }

  public void swipeLeft() {
    if (device == null) {
      return;
    }
    int midY = (int) (h/2);
    int swipeStep = (int) (w/5);
    device.swipe(w - swipeStep, midY, swipeStep, midY);
    RunTime.pause(waitAfterAction);
  }

  public void swipeRight() {
    if (device == null) {
      return;
    }
    int midY = (int) (h/2);
    int swipeStep = (int) (w/5);
    device.swipe(swipeStep, midY, w - swipeStep, midY);
    RunTime.pause(waitAfterAction);
  }

  //-----------------------------Overrides
  @Override
  public IScreen getScreen() {
    return this;
  }

  @Override
  public void update(EventSubject s) {
    waitPrompt = false;
  }

  @Override
  public IRobot getRobot() {
    return robot;
  }

  @Override
  public Rectangle getBounds() {
    return bounds;
  }

  @Override
  public ScreenImage capture() {
    return capture(bounds);
  }

  @Override
  public ScreenImage capture(int x, int y, int w, int h) {
    Rectangle rect = newRegion(new Location(x, y), w, h).getRect();
    return capture(rect);
  }

  @Override
  public ScreenImage capture(Region reg) {
    return capture(reg.getRect());
  }

  @Override
  public ScreenImage capture(Rectangle rect) {
    ScreenImage simg = null;
    if (robot != null) {
      log(logLvl, "ADBScreen.capture: (%d,%d) %dx%d", rect.x, rect.y, rect.width, rect.height);
      simg = device.captureScreen(rect);
    } else {
      log(-1, "capture: no ADBRobot available");
    }
    lastScreenImage = simg;
    return simg;
  }

  public void showTarget(Location loc) {
    showTarget(loc, Settings.SlowMotionDelay);
  }

  protected void showTarget(Location loc, double secs) {
    if (Settings.isShowActions()) {
      ScreenHighlighter overlay = new ScreenHighlighter(this, null);
      overlay.showTarget(loc, (float) secs);
    }
  }

  @Override
  public int getID() {
    return 0;
  }

  @Override
  public ScreenImage getLastScreenImageFromScreen() {
    return lastScreenImage;
  }

  @Override
  public ScreenImage userCapture(final String msg) {
    if (robot == null) {
      return null;
    }
    waitPrompt = true;
    Thread th = new Thread() {
      @Override
      public void run() {
        prompt = new OverlayCapturePrompt(ADBScreen.this);
        prompt.prompt(msg);
      }
    };

    th.start();

    boolean hasShot = false;
    ScreenImage simg = null;
    int count = 0;
    while (!hasShot) {
      this.wait(0.1f);
      if (count++ > waitForScreenshot) {
        break;
      }
      if (prompt == null) {
        continue;
      }
      if (prompt.isComplete()) {
        simg = prompt.getSelection();
        if (simg != null) {
          lastScreenImage = simg;
          hasShot = true;
        }
        prompt.close();
      }
    }
    prompt.close();
    prompt = null;
    return simg;
  }

  @Override
  public int getIdFromPoint(int srcx, int srcy) {
    return 0;
  }


  public Region newRegion(Location loc, int width, int height) {
    return new Region(loc.x, loc.y, width, height, this);
  }
}