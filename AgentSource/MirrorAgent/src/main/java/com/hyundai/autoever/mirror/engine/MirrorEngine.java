package com.hyundai.autoever.mirror.engine;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.imagerecognition.*;
import com.hyundai.autoever.utils.*;
import com.hyundai.autoever.mirror.engine.io.RemoteMessageWriter;
import com.hyundai.autoever.mirror.engine.query.AbstractRemoteCommand;
import com.hyundai.autoever.mirror.engine.query.AutoSwipeScript;
import com.hyundai.autoever.mirror.engine.query.FindScript;
import com.hyundai.autoever.mirror.engine.query.TouchScript;
import com.hyundai.autoever.mirror.engine.type.*;

public class MirrorEngine {
	private static final Logger LOG = Logger.getLogger(MirrorEngine.class);
	
	private static final boolean USE_DEBUG = false;
	
	public static final int MAX_RETRY_CONNECT = 10;
	
	private static final String MIRROR_START_LOGO = "am start -a MIRROR_ACTION_LOGO -n com.hyundai.autoever.mirrorservice/com.hyundai.autoever.mirrorservice.LogoActivity --ei showtimeout 3000";
	
	private static final String MIRROR_DUMPSYS_WINDOW_COMMAND = "dumpsys window";
	private static final String MIRROR_DUMPSYS_DISPLAY_COMMAND = "dumpsys display";
	
	private static final String KEYEVENT_DOWN_COMMAND = "sendevent %s 1 %d 1\n";
	private static final String KEYEVENT_UP_COMMAND = "sendevent %s 1 %d 0\n";
	private static final String KEYEVENT_COMMIT_COMMAND = "sendevent %s 0 0 0\n";
	
	private final String[] mKeyNames = new String[]{"KEYCODE_VOLUME_UP", "KEYCODE_VOLUME_DOWN", "KEYCODE_APP_SWITCH", "KEYCODE_HOME", "KEYCODE_MENU", "KEYCODE_BACK", "KEYCODE_POWER","KEYCODE_WAKEUP"};
	private final int[] mKeyCodes = new int[]{Constant.KEY_VOLUMEUP, Constant.KEY_VOLUMEDOWN, Constant.KEY_APP_SWITCH, Constant.KEY_HOMEPAGE, Constant.KEY_MENU, Constant.KEY_BACK, Constant.KEY_POWER, Constant.KEY_WAKEUP};
	private String[] mKeyDevices = new String[]{"","","","","","","",""};
	
	private final String[] mRotationNames = new String[]{"ROTATION_AUTO", "ROTATION_0", "ROTATION_90", "ROTATION_180", "ROTATION_270"};
	private final int[] mRotationValues = new int[]{-1, 0, 1, 2, 3};

	private final String[] mOrientationNames = new String[]{"ORIENTATION_TOGGLE", "ORIENTATION_PORTRAIT", "ORIENTATION_LANDSCAPE"};
	private final int[] mOrientationValues = new int[]{-1, 0, 1};
	
	private final double MAX_COMP_FRAME = 100000000.0f;
	private final double LIMIT_COMP_FRAME = 99999999.0f;
	
	private int MIN_VIRTUAL_WH = 540;
	private int MIRROR_PORT = 1717;
	private int TOUCH_PORT = 1718;
	//public int KBD_PORT = 1719;
	public int AGENT_PORT = 1720;
	public int SERVICE_PORT = 1721;
	
	private volatile Window mWindow = new Window();
	private volatile Banner mBanner = new Banner();
	private volatile Touch mTouch = new Touch();
	private volatile IDevice mDevice;
	
	private ConfigFile mCfg;
	
	private ExecutorService mExecutorCollecting = null;
	private ExecutorService mExecutorTouching = null;
	private ExecutorService mExecutorResulting = null;
	private ScheduledExecutorService mExecutorHold = null;
	private Thread mFrameThread = null, mConvertThread = null, mTouchThread = null;
	private ImageBinaryFrameCollector mFrameCollector = null;
	private ImageConverter mImageConverter = null;
	private ScreenTouchActivator mTouchActivator = null;
	private ClientService mClientService = null;
	private ResultService mResultService = null;
	private volatile boolean isRunning = false;
	private volatile boolean isCollecting = false;
	private volatile boolean isStopping = false;
	private volatile boolean isUseDetector = true;
	private volatile boolean isTouchDown = false;
	private volatile boolean isTouchHold = false;
	protected volatile boolean isCancelSwipe = false;
	protected volatile boolean isCancelAutoSwipe = false;
	protected volatile boolean isCancelFind = false;
	private volatile long mUnChangeTime = 0;
	private volatile long mLastChangeTime = 0;
	
	private Object mSync = new Object();
	private Object mSyncDetect = new Object();
	private Object mSyncDevice = new Object();
	
	private Queue<SoftReference<byte[]>> mPacketQueue = new LinkedBlockingQueue<SoftReference<byte[]>>();
	private Queue<byte[]> mTouchQueue = new LinkedBlockingQueue<byte[]>();
	private List<AndroidScreenObserver> mObservers = new ArrayList<AndroidScreenObserver>();
	
	private volatile SoftReference<BufferedImage> mDetectImage = null;
	private volatile SoftReference<BufferedImage> mFinalImage = null;
	
	private String mDeviceName = "";
	private String mDeviceModel = "";
	private String mDeviceSerial = "";
	
	private volatile int mDeviceIdx = -1;
	private volatile int mUxIdx = -1;
	private volatile boolean mDeviceEnable = false;
	private volatile boolean mDeviceVisible = false;
	
	public MirrorEngine(IDevice dev) {
		mDevice = dev;
		mDeviceName = dev.getName();
		mDeviceModel = dev.getProperty(IDevice.PROP_DEVICE_MODEL);
		mDeviceSerial = dev.getSerialNumber();
		setDeviceEnable(true);
		setDeviceVisible(true);
		
		mCfg = ConfigFile.get();
	}
	
	public void setDeviceIdx(int idx) {
		synchronized(mSyncDevice) {
			mDeviceIdx = idx;
		}
	}
	
	public int getDeviceIdx() {
		synchronized(mSyncDevice) {
			return mDeviceIdx;
		}
	}
	
	public void setUxIdx(int idx) {
		synchronized(mSyncDevice) {
			mUxIdx = idx;
		}
	}
	
	public int getUxIdx() {
		synchronized(mSyncDevice) {
			return mUxIdx;
		}
	}
	
	public void setDeviceEnable(boolean enable) {
		synchronized(mSyncDevice) {
			mDeviceEnable = enable;
		}
	}
	
	public boolean isDeviceEnable() {
		synchronized(mSyncDevice) {
			return mDeviceEnable;
		}
	}
	
	public void setDeviceVisible(boolean visible) {
		synchronized(mSyncDevice) {
			mDeviceVisible = visible;
		}
	}
	
	public boolean isDeviceVisible() {
		synchronized(mSyncDevice) {
			return mDeviceVisible;
		}
	}
	
	public static MirrorEngine create(ADB adb, String serial) {
		if(serial == null || serial.isEmpty())
			return null;
		
		if(!adb.initDevices())
			return null;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return null;
		
		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getSerialNumber().equalsIgnoreCase(serial)) {
				MirrorEngine engine =  new MirrorEngine(dev);
				if(!engine.init())
					return null;
				return engine;
			}
		}
		return null;
	}
	
	public boolean init() {
		if(!load())
			return false;
		
		notifyObservers(mWindow);
		return true;
	}
	
	public void init(AndroidScreenObserver observer) {
		registerObserver(observer);
		init();
	}
	
	private boolean loadWindow(JSONObject objRoot) {
		if(objRoot == null) {
			mCfg.load();
			objRoot = mCfg.getObject();
		}
		
		try {
			JSONObject objDev = objRoot.getJSONObject(mDeviceName);
			if(objDev == null)
				return false;
			
			JSONObject objWin = objDev.getJSONObject("window");
			if(objWin == null)
				return false;
			
			mWindow.fromJson(objWin);
		} catch(JSONException e) {
			AnsiLog.e(9210, "[" + mDeviceModel + "] " + e.toString());
		}
		return true;
	}
	
	private boolean loadPort(JSONObject objRoot) {
		boolean newLoad = false;
		if(objRoot == null) {
			newLoad = true;
			mCfg.load();
			objRoot = mCfg.getObject();
		}
		
		try {
			JSONObject objDev = objRoot.getJSONObject(mDeviceName);
			if(objDev == null)
				return false;
			
			JSONObject objPort = objDev.getJSONObject("port");
			if(objPort == null)
				return false;
			
			MIRROR_PORT = objPort.getInt("mirror");
			TOUCH_PORT = objPort.getInt("touch");
			//KBD_PORT = objPort.getInt("kbd");
			AGENT_PORT = objPort.getInt("agent");
			SERVICE_PORT = objPort.getInt("service");
			
			if(newLoad) {
				AnsiLog.i2(String.format(
					"[%-11s:%17s] MIRROR_PORT: %5d, TOUCH_PORT: %5d, AGENT_PORT: %5d, SERVICE_PORT: %5d", 
					mDeviceModel, mDeviceSerial, 
					MIRROR_PORT, TOUCH_PORT, AGENT_PORT, SERVICE_PORT));
			}			
		} catch(JSONException e) {
			AnsiLog.e(9210, "[" + mDeviceModel + "] " + e.toString());
		}
		return true;
	}
	
	private boolean loadKeyDev(JSONObject objRoot) {
		if(objRoot == null) {
			mCfg.load();
			objRoot = mCfg.getObject();
		}
		
		try {
			JSONObject objDev = objRoot.getJSONObject(mDeviceName);
			if(objDev == null)
				return false;
			
			JSONObject objKey = objDev.getJSONObject("key");
			if(objKey == null)
				return false;
			
			JSONArray arrKey = objKey.getJSONArray("devs");
			if(arrKey == null)
				return false;
			
			int len = Math.min(mKeyDevices.length, arrKey.length());
			for(int i=0;i<len;i++)
				mKeyDevices[i] = arrKey.getString(i);
			
		} catch(JSONException e) {
			AnsiLog.e(9210, "[" + mDeviceModel + "] " + e.toString());
		}
		return true;
	}
	
	private boolean load() {
		mCfg.load();
		JSONObject objRoot = mCfg.getObject();
		
		if(!loadWindow(objRoot))
			return false;
		
		if(!loadPort(objRoot))
			return false;

		if(!loadKeyDev(objRoot))
			return false;
			
		String log = String.format(
				"[%-11s:%17s] Screen Size: %4d-%4d, Screen DPI: %3.3f-%3.3f, MIRROR_PORT: %5d, TOUCH_PORT: %5d, AGENT_PORT: %5d, SERVICE_PORT: %5d", 
				mDeviceModel, mDeviceSerial, 
				mWindow.getPhysicalWidth(), mWindow.getPhysicalHeight(), 
				mWindow.getDpiX(), mWindow.getDpiY(),
				MIRROR_PORT, TOUCH_PORT, AGENT_PORT, SERVICE_PORT);
		AnsiLog.i2(log);
		return true;
	}
			
	public boolean initWindow() {
		Window window = dumpWindow();
		if(window == null)
			return false;
		
		boolean bBanner = false;
		mWindow.set(window);
//		if(mBanner.getVersion() > 0 && 
//			(window.getVirtualWidth() != mBanner.getVirtualWidth() || 
//			window.getVirtualHeight() != mBanner.getVirtualHeight())) {
//			mBanner.setReadWidth(window.getAppWidth());
//			mBanner.setReadHeight(window.getAppHeight());
//			mBanner.setVirtualWidth(window.getVirtualWidth());
//			mBanner.setVirtualHeight(window.getVirtualHeight());
//			bBanner = true;
//		}
		
		notifyObservers(mWindow);
		if(bBanner)
			notifyObservers(mBanner);
		
		AnsiLog.d("[" + mDeviceModel + "] " + "Window info: " + window.toString());
		return true;
	}
	
	private Window dumpWindow() {
		Window window = new Window();
		
		window.setName(mDeviceName);
		window.setSerial(mDeviceSerial);
		window.setModel(mDeviceModel);
		
		Pattern p;
		Matcher m;
		String output = executeShellCommand(MIRROR_DUMPSYS_WINDOW_COMMAND);
		
		p = Pattern.compile("mCurrentRotation\\=([\\-0-9]+)");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 1) { // groupCount bug???(+1)
				try {
					window.setRotation(Integer.valueOf(m.group(1)));
				} catch(NumberFormatException e) {}
			}
		}
		else {
			String sRotateKeyword = "SurfaceOrientation:";
			String sRotateCheckCmd = "dumpsys input | grep -Eo " + sRotateKeyword + "[\\ 0-9]+";
			String outputRotation = executeShellCommand(sRotateCheckCmd).replace(sRotateKeyword, "").trim();
			if(!outputRotation.isEmpty()) {
				int f;
				if((f = outputRotation.indexOf("\r")) > 0 || (f = outputRotation.indexOf("\n")) > 0)
					outputRotation = outputRotation.substring(0, f);
				try {
					window.setRotation(Integer.valueOf(outputRotation), true);
				} catch(NumberFormatException e) {}
			}
		}
		
		p = Pattern.compile("Display\\: mDisplayId\\=(\\d+)\\s+init\\=(\\d+)x(\\d+) .*cur\\=(\\d+)x(\\d+) .*app\\=(\\d+)x(\\d+)");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 7) { // groupCount bug???(+1)
				try {
					window.setDisplayId(Integer.valueOf(m.group(1)));
					window.setPhysicalWidth(Integer.valueOf(m.group(2)));
					window.setPhysicalHeight(Integer.valueOf(m.group(3)));
					window.setCurrentWidth(Integer.valueOf(m.group(4)));
					window.setCurrentHeight(Integer.valueOf(m.group(5)));
					window.setAppWidth(Integer.valueOf(m.group(6)));
					window.setAppHeight(Integer.valueOf(m.group(7)));
					
				} catch(NumberFormatException e) {}
			}
		}
		if(window.getPhysicalWidth() == 0) {
			AnsiLog.e(9204, "[" + mDeviceModel + "] " + "Failed to get the screen RESOLUTION of Android.");
			return null;
		}
		
		int b, s, b2, s2;
		b = Math.max(window.getCurrentWidth(), window.getCurrentHeight());
		s = Math.min(window.getCurrentWidth(), window.getCurrentHeight());
		s2 = MIN_VIRTUAL_WH; 
		b2 = s2 * b / s;
		if(window.getCurrentWidth() > window.getCurrentHeight()) {
			window.setVirtualWidth(b2);
			window.setVirtualHeight(s2);
		}
		else {
			window.setVirtualWidth(s2);
			window.setVirtualHeight(b2);
		}
		
		p = Pattern.compile("mOrientationSensorEnabled\\=([a-z]+)");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 1) { // groupCount bug???(+1)
				try {
					window.setSensor(Boolean.valueOf(m.group(1)));
				} catch(NumberFormatException e) {}
			}
		}
		
		output = executeShellCommand(MIRROR_DUMPSYS_DISPLAY_COMMAND);
		p = Pattern.compile("DisplayDeviceInfo\\{.+density ([0-9\\.]+), ([0-9\\.]+) x ([0-9\\.]+) dpi");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 3) { // groupCount bug???(+1)
				try {
					window.setDensity(Integer.valueOf(m.group(1)));
					window.setDpiX(Double.valueOf(m.group(2)));
					window.setDpiY(Double.valueOf(m.group(3)));
					
				} catch(NumberFormatException e) {}
			}
		}
		
		return window;
	}

	public void uninit() {
		mBanner.reset();
		setDetectImage(null);
		mPacketQueue.clear();
		mTouchQueue.clear();

		System.gc();
	}
	
	public Window getWindow() {
		return mWindow;
	}
	
	public Banner getBanner() {
		return mBanner;
	}
	
	public boolean start() {
		setRunning(true);
		if(isStopping()) {
			for(int i=0;i<300 && isStopping();i++) {
				try { Thread.sleep(10); } catch (InterruptedException e) {}
			}
		}
		loadPort(null);
		
		startCollecting();
		startTouching();
		sendWake();
		
		return true;
	}
	
	public void stop() {
		setRunning(false);
		setStopping(true);
		stopCollecting();
		stopTouching();
		
		uninit();
		setStopping(false);
	}
	
	public boolean startCollecting() {
		if(mDeviceModel.equals("SM-G900L"))
			AnsiLog.d("[" + mDeviceModel + "] " + "filtering");
		
		mUnChangeTime = 0;
		mLastChangeTime = 0;
		mFrameCollector = new ImageBinaryFrameCollector();
		mImageConverter = new ImageConverter();
		mClientService = new ClientService(this);
		
		setCollecting(true);
		mExecutorCollecting = Executors.newFixedThreadPool(3);
		mExecutorCollecting.submit(mFrameCollector);
		mExecutorCollecting.submit(mImageConverter);
		mExecutorCollecting.submit(mClientService);

		return true;
	}
	
	public void stopCollecting() {
		setCollecting(false);
		try { Thread.sleep(1); } catch (InterruptedException e) {}
		
		mFrameCollector = null;
		mImageConverter = null;
		if(mClientService != null) {
			mClientService.stop();
		}
		
		try {
    	    mExecutorCollecting.shutdown();
    	    mExecutorCollecting.awaitTermination(2000, TimeUnit.MILLISECONDS);
    	}
    	catch (InterruptedException e) {
    	}
    	finally {
    	    if (!mExecutorCollecting.isTerminated()) {
        	    mExecutorCollecting.shutdownNow();
    	    }
    	}

		System.gc();
	}

	public boolean startTouching() {
		mExecutorTouching = Executors.newCachedThreadPool();
		mExecutorHold = Executors.newSingleThreadScheduledExecutor();
		
		mTouchActivator = new ScreenTouchActivator();
		mExecutorTouching.submit(mTouchActivator);
		
		if(ServerModule.get().isUseSetResult()) {
			mExecutorResulting = Executors.newCachedThreadPool();
			mResultService = new ResultService(this);
			mExecutorResulting.submit(mResultService);
		}
		return true;
	}
	
	public void stopTouching() {
		if(isTouchHold) {
			mExecutorHold.shutdownNow();
			isTouchHold = false;
		}
		
		if(mExecutorTouching != null) {
			try {
				mExecutorTouching.shutdown();
				mExecutorTouching.awaitTermination(2000, TimeUnit.MILLISECONDS);
	    	}
	    	catch (InterruptedException e) {
	    	}
	    	finally {
	    	    if (!mExecutorTouching.isTerminated()) {
	    	    	mExecutorTouching.shutdownNow();
	    	    }
	    	}
		}
		
		if(mExecutorResulting != null) {
			try {
				mExecutorResulting.shutdown();
				mExecutorResulting.awaitTermination(2000, TimeUnit.MILLISECONDS);
	    	}
	    	catch (InterruptedException e) {
	    	}
	    	finally {
	    	    if (!mExecutorResulting.isTerminated()) {
	    	    	mExecutorResulting.shutdownNow();
	    	    }
	    	}
		}
		
		mTouchActivator = null;
		mResultService = null;

		System.gc();
	}
	
	public ClientService getClientService() {
		return mClientService;
	}

	public ResultService getResultService() {
		return mResultService;
	}
	
	public ScriptRunner createScriptRunner(CommandServer.ConnectionCommandWrap wrap, JSONObject json) {
		return new ScriptRunner(this, wrap, json);
	}
	
	public ScriptRunner createScriptRunner(CommandServer.ConnectionCommandWrap wrap, SoftReference<BufferedImage> cropImage, JSONObject json) {
		return new ScriptRunner(this, wrap, cropImage, json);
	}
	
	public void setRunning(boolean running) {
		synchronized(mSync) {
			isRunning = running;
		}
	}
	
	public boolean isRunning() {
		synchronized(mSync) {
			return isRunning;
		}
	}

	public void setCollecting(boolean collecting) {
		synchronized(mSync) {
			isCollecting = collecting;
		}
	}
	
	public boolean isCollecting() {
		synchronized(mSync) {
			return isCollecting;
		}
	}
	
	public void setStopping(boolean Stopping) {
		synchronized(mSync) {
			isStopping = Stopping;
		}
	}
	
	public boolean isStopping() {
		synchronized(mSync) {
			return isStopping;
		}
	}
	
	public void setTouchDown(boolean touchDown) {
		synchronized(mSync) {
			isTouchDown = touchDown;
		}
	}
	
	public boolean isTouchDown() {
		synchronized(mSync) {
			return isTouchDown;
		}
	}

	class ImageBinaryFrameCollector implements Runnable {
		private int retryConn = 0;
		
		public ImageBinaryFrameCollector() {
			executeShellCommand(MIRROR_START_LOGO);
		}
		
		public void run() {
			mFrameThread = Thread.currentThread();
			
			mPacketQueue.clear();

			AnsiLog.i(1006, "[" + mDeviceModel + "] " + "Start Connecting to the MIRROR-MODULE of Android.");
			
			Socket socketFrame = null;
			InputStream streamFrame = null;
			
			while (isRunning() && isCollecting() && mFrameThread != null && !mFrameThread.isInterrupted()) {
				AnsiLog.d("[" + mDeviceModel + "] " + "Retry ImageBinaryFrameCollector");
				try {
					int someTimeout = 1000;
					AnsiLog.i(1006, "[" + mDeviceModel + "] " + "Connecting to the MIRROR-MODULE(" + MIRROR_PORT + ").");
					socketFrame = new Socket("localhost", MIRROR_PORT);
					socketFrame.setSoTimeout(someTimeout);
					streamFrame = socketFrame.getInputStream();
					// input = new DataInputStream(stream);
					long maxTic = 500;
					long startTic = System.currentTimeMillis();
					int len = 10240;
					while (isRunning() && isCollecting() && mFrameThread != null && !mFrameThread.isInterrupted()) {
						SoftReference<byte[]> buffer = new SoftReference<byte[]>(new byte[len]);
						int realLen = 0;
						try { realLen = streamFrame.read(buffer.get()); } catch(SocketTimeoutException e) {}
						
						if(USE_DEBUG) {
							String devModel = mDeviceModel;
							if(mDeviceModel.equals("SM-N900K"))
								devModel = mDeviceModel;
						}
						
						if(realLen > 0) {
							if (buffer.get().length != realLen) {
								buffer = subByteArray(buffer.get(), 0, realLen);
							}
							//AnsiLog.d("[" + mDeviceModel + "] " + "packet add");
							mPacketQueue.add(buffer);
							if(System.currentTimeMillis() - startTic > maxTic) {
								try { Thread.sleep(1); } catch (InterruptedException e) {}
								startTic = System.currentTimeMillis();
							}
							else {
								Thread.yield();
							}
						}
						else {
							buffer.clear();
							if(realLen < 0)
								break;
							try { Thread.sleep(1); } catch (InterruptedException e) {}
							//AnsiLog.dp("line : " + mFrameThread.getStackTrace()[2].getLineNumber());
						}
					}
				} catch (IOException e) {
					//e.printStackTrace();
					if(isRunning() && isCollecting()) {
						retryConn++;
						if(retryConn >= MirrorEngine.MAX_RETRY_CONNECT) {
							setRunning(false);
							setCollecting(false);
						}
					}
				} finally {
					if (socketFrame != null && socketFrame.isConnected()) {
						try {
							socketFrame.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (streamFrame != null) {
						try {
							streamFrame.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				
				if(isRunning() && isCollecting() && mFrameThread != null && !mFrameThread.isInterrupted()) {
					mPacketQueue.clear();
					mImageConverter.reset();
					mTouch.reset();
					setDetectImage(null);
					try { Thread.sleep(1000); } catch (InterruptedException e) {}
				}
			}

			System.gc();
			AnsiLog.i(1016, "[" + mDeviceModel + "] " + "The MIRROR-MODULE connection of Android has been ENDED.");
		}
	}
	
	class ScreenTouchActivator implements Runnable {
		private BufferedReader mReaderTouch = null;
		private BufferedOutputStream mStreamTouch = null;
		private int retryConn = 0;
		private boolean isRun = true;
		
		public void run() {
			AnsiLog.i(1007, "[" + mDeviceModel + "] " + "Start the TOUCH-MODULE.");

			mTouchThread = Thread.currentThread();
			
			Socket socketTouch = null;
			
			while (isRunning() && isRun && !mTouchThread.isInterrupted()) {
				AnsiLog.d("[" + mDeviceModel + "] " + "Retry ScreenTouchActivator");
				try {
					socketTouch = new Socket("localhost", TOUCH_PORT);
					mReaderTouch = new BufferedReader(new InputStreamReader(socketTouch.getInputStream()));
					mStreamTouch = new BufferedOutputStream(socketTouch.getOutputStream());
	
					String bufferInfo;
					for(int i=0,c=0;i<6 && c<3 && !Thread.interrupted();i++) {
						if(mReaderTouch.ready()) {
							if((bufferInfo = mReaderTouch.readLine()) != null) {
								if(!bufferInfo.isEmpty()) {
									if(bufferInfo.charAt(0) == 'v' && bufferInfo.length() > 2) {
										try {
											mTouch.setVersion(Integer.valueOf(bufferInfo.substring(2)));
										} catch(NumberFormatException e) {}
									}
									else if(bufferInfo.charAt(0) == '$' && bufferInfo.length() > 2) {
										try {
											mTouch.setPid(Integer.valueOf(bufferInfo.substring(2)));
										} catch(NumberFormatException e) {}
										break;
									}
									else if(bufferInfo.charAt(0) == '^' && bufferInfo.length() > 2) {
										Pattern p = Pattern.compile("\\^ (\\d+) (\\d+) (\\d+) (\\d+)");
										Matcher m = p.matcher(bufferInfo);
										if(m.find()) {
											if(m.groupCount() == 4) { // groupCount bug???(+1)
												try {
													mTouch.setMaxContacts(Integer.valueOf(m.group(1)));
													mTouch.setMaxX(Integer.valueOf(m.group(2)));
													mTouch.setMaxY(Integer.valueOf(m.group(3)));
													mTouch.setMaxPressure(Integer.valueOf(m.group(4)));
												} catch(NumberFormatException e) {}
											}
										}
									}
								}
								c++;
							}
						}
						else {
							Thread.sleep(400);
						}
					}
					AnsiLog.d("[" + mDeviceModel + "] " + "Touch info: " + mTouch.toString());
					
					while (isRunning() && isRun && mTouchThread != null && !mTouchThread.isInterrupted()) {
						if (mTouchQueue.isEmpty()) {
							try { Thread.sleep(1); } catch (InterruptedException e) {}
							continue;
						}
						byte[] buffer = mTouchQueue.poll();
						mStreamTouch.write(buffer);
						mStreamTouch.flush();
						try { Thread.sleep(1); } catch (InterruptedException e) {}
						
						buffer = null;
					}
				} catch (IOException e) {
					//e.printStackTrace();
					if(isRunning() && isRun) {
						retryConn++;
						if(retryConn >= MirrorEngine.MAX_RETRY_CONNECT) {
							isRun = false;
						}
					}
				} catch (InterruptedException e) {
				} finally {
					if (mReaderTouch != null) {
						try {
							mReaderTouch.close();
							mReaderTouch = null;
						} catch (IOException e) {}
					}
					if (mStreamTouch != null) {
						try {
							mStreamTouch.close();
							mStreamTouch = null;
						} catch (IOException e) {}
					}
					if (socketTouch != null && socketTouch.isConnected()) {
						try {
							socketTouch.close();
						} catch (IOException e) {}
					}
				}
				
				if(isRunning() && isRun && mTouchThread != null && !mTouchThread.isInterrupted()) {
					mTouchQueue.clear();
					try { Thread.sleep(1000); } catch (InterruptedException e) {}
				}
			}

			AnsiLog.i(1017, "[" + mDeviceModel + "] " + "Stop the TOUCH-MODULE.");
		}
	}

	class ImageConverter implements Runnable {
		private final int maxFirstFrames = 10;
		private int readBannerBytes = 0;
		private int bannerLength = 0;
		private int readFrameBytes = 0;
		private int frameBodyLength = 0;
		private int frameCnt = 0;
		private SoftReference<byte[]> frameBody = null;
		
		public ImageConverter() {
		}
		
		private void goBlankImage() {
			int crw = mWindow.getVirtualWidth();
			int crh = mWindow.getVirtualHeight();
			if(mFinalImage == null)
				mFinalImage = new SoftReference<BufferedImage>(new BufferedImage(crw, crh, BufferedImage.TYPE_3BYTE_BGR));
			notifyObservers(mFinalImage);
		}
		
		public void run() {
			mConvertThread = Thread.currentThread();
			
			AnsiLog.i(1008, "[" + mDeviceModel + "] " + "Start the IMAGE-ENCODER.");
			
			reset();
			
			//goBlankImage();
			
			long yd = 1000;
			long start = System.currentTimeMillis(), now = 0;
			SoftReference<byte[]> finalBytes = null;
			int crw, crh;
			
			while (isRunning() && isCollecting() && mConvertThread != null && !mConvertThread.isInterrupted()) {
				if (mPacketQueue.isEmpty()) {
					try { Thread.sleep(1); } catch (InterruptedException e) {}
					continue;
				}
				SoftReference<byte[]> buffer = mPacketQueue.poll();
				int len = buffer.get().length;
				for (int cursor = 0; cursor < len;) {
					int byte10 = buffer.get()[cursor] & 0xff;
					if (readBannerBytes < bannerLength) {
						cursor = parserBanner(cursor, byte10);
					} else if (readFrameBytes < 4) {
						frameBodyLength += (byte10 << (readFrameBytes * 8)) >>> 0;
						cursor += 1;
						readFrameBytes += 1;
					} else {
						if (len - cursor >= frameBodyLength) {
							SoftReference<byte[]> subByte = subByteArray(buffer.get(), cursor, cursor + frameBodyLength);
							frameBody = byteMerger(frameBody.get(), subByte.get());
							if ((frameBody.get()[0] != -1) || frameBody.get()[1] != -40) {
								AnsiLog.e(9211, "[" + mDeviceModel + "] " + String.format("Frame body does not start with JPG header"));
								return;
							}
							SoftReference<byte[]> imageBytes = subByteArray(frameBody.get(), 0, frameBody.get().length);
							boolean isframe = false;

							if(ServerModule.get().isUseOpengl()) {
								SoftReference<BufferedImage> image = ImageUtil.createImageFromByteRef(imageBytes.get());
								if(image != null) {
									crw = mWindow.getVirtualWidth();
									crh = mWindow.getVirtualHeight();
									if(crw != image.get().getWidth() || crh != image.get().getHeight()) {
										SoftReference<BufferedImage> imageSrc = ImageUtil.cropImageRef(image.get(), 0, 0, crw, crh);
										image.clear();
										image = imageSrc;
									}
								}
								if(image != null) {
//									if(frameCnt < maxFirstFrames || 
//										ImageUtil.opencv_compareChangeImage(mFinalImage.get(), image.get(), MAX_COMP_FRAME, LIMIT_COMP_FRAME))
//									{
//										mUnChangeTime = 0;
//									}
//									else {
//										if(mUnChangeTime == 0)
//											mUnChangeTime = System.currentTimeMillis();
//									}
									
									if(USE_DEBUG) {
										String devModel = mDeviceModel;
										if(mDeviceModel.equals("SM-N900K"))
											devModel = mDeviceModel;
									}
									
									//setFinalImage(image);
									if(isUseDetector)
										setDetectImage(image);
									notifyObservers(image);
									frameCnt++;
									isframe = true;
									image.clear();
								}
//								if(image != null) {
//									if(frameCnt < maxFirstFrames || 
//										ImageUtil.opencv_compareChangeImage(mFinalImage.get(), image.get(), MAX_COMP_FRAME, LIMIT_COMP_FRAME))
//									{
//										setFinalImage(image);
//										if(isUseDetector)
//											setDetectImage(image);
//										notifyObservers(image);
//										frameCnt++;
//										isframe = true;
//										image.clear();
//								
//										mUnChangeTime = 0;
//									}
//									else {
//										if(mUnChangeTime == 0)
//											mUnChangeTime = System.currentTimeMillis();
//									}
//								}
							}
							else {
								double compFrame = 0;
								if(frameCnt < maxFirstFrames || finalBytes == null || 
									(compFrame = ImageUtil.compareByteArrays(finalBytes.get(), imageBytes.get(), MAX_COMP_FRAME)) < LIMIT_COMP_FRAME) {
									finalBytes = copyByteArray(imageBytes.get());
									SoftReference<BufferedImage> image = ImageUtil.createImageFromByteRef(imageBytes.get());
									if(image != null) {
										crw = mWindow.getVirtualWidth();
										crh = mWindow.getVirtualHeight();
										if(crw != image.get().getWidth() || crh != image.get().getHeight()) {
											SoftReference<BufferedImage> imageSrc = ImageUtil.cropImageRef(image.get(), 0, 0, crw, crh);
											//SoftReference<BufferedImage> imageSrc = ImageUtil.cropImageRefExceptBlack(image.get(), Math.min(crw, crh), Math.max(crw, crh), crw > crh);
											image.clear();
											image = imageSrc;
										}
									}
									
									if(image != null) {
										//setFinalImage(image);
										
										if(isUseDetector)
											setDetectImage(image);
										notifyObservers(image);
										
										frameCnt++;
										isframe = true;
										image.clear();
										
										mUnChangeTime = 0;
									}
								}
								else {
									if(mUnChangeTime == 0)
										mUnChangeTime = System.currentTimeMillis();
								}
							}
							if(imageBytes != null)
								imageBytes.clear();
							imageBytes = null;
							
							now = System.currentTimeMillis();
							if(now-start > yd) {
								try { Thread.sleep(1); } catch (InterruptedException e) {}
							}
							start = now;
							cursor += frameBodyLength;
							
							//if(isframe)
								restore();
							
						} else {
							SoftReference<byte[]> subByte = subByteArray(buffer.get(), cursor, len);
							frameBody = byteMerger(frameBody.get(), subByte.get());
							frameBodyLength -= (len - cursor);
							readFrameBytes += (len - cursor);
							cursor = len;
						}
					}
				}
				buffer.clear();
				//try { Thread.sleep(1); } catch (InterruptedException e) {}
			}

			AnsiLog.i(1018, "[" + mDeviceModel + "] " + "The IMAGE-ENCODER has been ENDED.");
		}
		
		public void reset() {
			AnsiLog.d("[" + mDeviceModel + "] " + "Reset ImageConverter");
			mBanner.reset();
			frameCnt = 0;
			readBannerBytes = 0;
			bannerLength = 2;
			restore();
			if(isUseDetector)
				setDetectImage(null);
		}

		private void restore() {
			frameBodyLength = 0;
			readFrameBytes = 0;
			frameBody = new SoftReference<byte[]>(new byte[0]);
		}

		private int parserBanner(int cursor, int byte10) {
			switch (readBannerBytes) {
			case 0:
				// version
				mBanner.reset();
				mBanner.setVersion(byte10);
				AnsiLog.d("[" + mDeviceModel + "] " + "Banner Version: " + mBanner.getVersion());
				break;
			case 1:
				// length
				bannerLength = byte10;
				mBanner.setLength(byte10);
				break;
			case 2:
			case 3:
			case 4:
			case 5:
				// pid
				int pid = mBanner.getPid();
				pid += (byte10 << ((readBannerBytes - 2) * 8)) >>> 0;
				mBanner.setPid(pid);
				break;
			case 6:
			case 7:
			case 8:
			case 9:
				// real width
				int realWidth = mBanner.getReadWidth();
				realWidth += (byte10 << ((readBannerBytes - 6) * 8)) >>> 0;
				mBanner.setReadWidth(realWidth);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
				// real height
				int realHeight = mBanner.getReadHeight();
				realHeight += (byte10 << ((readBannerBytes - 10) * 8)) >>> 0;
				mBanner.setReadHeight(realHeight);
				break;
			case 14:
			case 15:
			case 16:
			case 17:
				// virtual width
				int virtualWidth = mBanner.getVirtualWidth();
				virtualWidth += (byte10 << ((readBannerBytes - 14) * 8)) >>> 0;
				mBanner.setVirtualWidth(virtualWidth);

				break;
			case 18:
			case 19:
			case 20:
			case 21:
				// virtual height
				int virtualHeight = mBanner.getVirtualHeight();
				virtualHeight += (byte10 << ((readBannerBytes - 18) * 8)) >>> 0;
				mBanner.setVirtualHeight(virtualHeight);
				break;
			case 22:
				// orientation
				mBanner.setOrientation(byte10 * 90);
				break;
			case 23:
				// quirks
				mBanner.setQuirks(byte10);
				break;
			}

			cursor += 1;
			readBannerBytes += 1;

			if (readBannerBytes == bannerLength) {
				AnsiLog.d("[" + mDeviceModel + "] " + "banner info : " + mBanner.toString());
				notifyObservers(mBanner);
			}
			return cursor;
		}

	}
	
	public ImageLocation doActionCropEvent(CommandServer.ConnectionCommandWrap wrap, String method, JSONObject args) {
		ImageLocation l = null;
		int x = 0, y = 0, cx = 0, cy = 0, crw = 0, crh = 0, dw, dh;
		boolean shot = !args.has("crw") && !args.has("crw");
		SoftReference<BufferedImage> cropImage = null, shotImage = null;
		
		shotImage = getDetectImage();
		if(shot) {
			if(shotImage != null) {
				cropImage = shotImage;
				
				crw = cropImage.get().getWidth();
				crh = cropImage.get().getHeight();
			}
		}
		else {
			try {
				boolean center = args.has("cx") && args.has("cy");
				
				crw = args.getInt("crw");
				crh = args.getInt("crh");
				
				dw = crw/2; dw += crw%2;
				dh = crh/2; dh += crh%2;
				
				if(center) {
					cx = args.getInt("cx");
					cy = args.getInt("cy");
					
					x = cx - dw; if(x < 0) x = 0;
					y = cy - dh; if(y < 0) y = 0;
					
					cropImage = getCenterCropDetectImage(cx, cy, crw, crh);
				}
				else {
					x = args.getInt("x");
					y = args.getInt("y");
					
					cx = x + dw;
					cy = y + dh;
					cropImage = getCropDetectImage(x, y, crw, crh);
				}
			} catch(Exception e) {}
		}
		
		if(cropImage != null) {
			l = new ImageLocation();
			l.setTopLeft(new Point(x, y));
			l.setBottomRight(new Point(x+crw, y+crh));
			l.setQueryImage(cropImage);
			l.setShotImage(shotImage);
			l.encodeBase64Image();
		}
		if(shotImage != null)
			shotImage.clear();
		return l;
	}
	
	public ImageLocation doActionFindEvent(CommandServer.ConnectionCommandWrap wrap, String method, JSONObject args, SoftReference<BufferedImage> queryImage) {
		ImageLocation l = null;

		try {
			JSONObject json = null;
			int limit = FindScript.DEFAULT_LIMIT_TIME;
			double similarity = FindScript.DEFAULT_SIMILARITY;
			long start, end, now = 0;
			double rzX = 0, rzY = 0;
			
			json = getCalcFind(wrap);
			rzX = json.getDouble("rzX");
			rzY = json.getDouble("rzY");
			
			if(args.has("limit"))
				limit = args.getInt("limit");
			if(args.has("similarity"))
				similarity = args.getDouble("similarity");
			
			AnsiLog.d("[" + mDeviceModel + "] " + "Starting Find Image");
			start = System.currentTimeMillis();
			while(true) {
				end = System.currentTimeMillis();
				if(end-start > limit) {
					AnsiLog.w("[" + mDeviceModel + "] " + "Timeout Find Image: " + limit);
					break;
				}
				if(isCancelFind) {
					AnsiLog.i("[" + mDeviceModel + "] " + "Cancel Find Image");
					break;
				}
				if(now != mLastChangeTime) {
					SoftReference<BufferedImage> sceneImage = getCloneDetectImage();
					if(sceneImage != null) {
						now = mLastChangeTime;
						
						SoftReference<BufferedImage> cropImage = queryImage;
						if(rzX != 1.000f || rzY != 1.000f) {
							int qx = queryImage.get().getWidth(), qy = queryImage.get().getHeight();
							int cw = (int)(rzX*qx);
							int ch = (int)(rzY*qy);
							cropImage = ImageUtil.resizeImageRef(queryImage.get(), cw, ch);
						}
						
						l = ImageRecognition.findImage(cropImage.get(), sceneImage.get(), similarity);
						if(l != null) {
							int crw = cropImage.get().getWidth();
							int crh = cropImage.get().getHeight();
							cropImage = ImageUtil.cropImageRef(sceneImage.get(), l.getX(), l.getY(), crw, crh);
							if(cropImage != null) {
								l.setQueryImage(cropImage);
								l.setShotImage(sceneImage);
								l.encodeBase64Image();
							}
							sceneImage.clear();
							AnsiLog.d("[" + mDeviceModel + "] " + "Found Image");
							break;
						}
						sceneImage.clear();
					}
				}
				Thread.sleep(10);
			}
			
		} catch(JSONException e) {
		} catch(InterruptedException e) {
		}
		return l;
	}
	
	public ImageLocation doActionAutoEvent(CommandServer.ConnectionCommandWrap wrap, String method, JSONObject args, SoftReference<BufferedImage> queryImage) {
		ImageLocation l = null;

		try {
			JSONObject json = null;
			int repeat = AutoSwipeScript.DEFAULT_REPEAT;
			int limit = AutoSwipeScript.DEFAULT_LIMIT_TIME;
			int wait = AutoSwipeScript.DEFAULT_WAIT_TIME;
			double similarity = AutoSwipeScript.DEFAULT_SIMILARITY;
			int min_unchange = 4000;
			int duration = 700;
			int startx, starty, destx, desty;
			int cnt;
			int ms;
			long start, end;
			double stepx, stepy;
			double rzX = 0, rzY = 0;
			double x = 0, y = 0;
			
			startx = args.getInt("startx");
			starty = args.getInt("starty");
			destx = args.getInt("destx");
			desty = args.getInt("desty");
			if(args.has("similarity"))
				similarity = args.getDouble("similarity");
			json = getCalcSwipe(wrap, startx, starty, destx, desty, duration);
			
			if(json.has("repeat"))
				repeat = json.getInt("repeat");
			if(json.has("limit"))
				limit = json.getInt("limit");
			if(json.has("wait"))
				wait = json.getInt("wait");
			ms = json.getInt("ms");
			cnt = json.getInt("cnt");
			startx = json.getInt("startx");
			starty = json.getInt("starty");
			destx = json.getInt("destx");
			desty = json.getInt("desty");
			duration = json.getInt("duration");
			stepx = json.getDouble("stepx");
			stepy = json.getDouble("stepy");
			rzX = json.getDouble("rzX");
			rzY = json.getDouble("rzY");
			
			
			while(!mTouchQueue.isEmpty()) {
				try { Thread.sleep(100); } catch(InterruptedException e) {}
			}
			
			AnsiLog.d("[" + mDeviceModel + "] " + "Starting Auto Swiping");
			start = System.currentTimeMillis();
			for(int j=0;j<repeat;j++) {
				end = System.currentTimeMillis();
				if(end-start > limit) {
					AnsiLog.w("[" + mDeviceModel + "] " + "Timeout Auto Swiping: " + limit);
					break;
				}
				if(isCancelAutoSwipe) {
					AnsiLog.i("[" + mDeviceModel + "] " + "Cancel Auto Swiping");
					break;
				}
				
				SoftReference<BufferedImage> cropImage = queryImage;
				if(rzX != 1.000f || rzY != 1.000f) {
					int qx = queryImage.get().getWidth(), qy = queryImage.get().getHeight();
					int cw = (int)(rzX*qx);
					int ch = (int)(rzY*qy);
					cropImage = ImageUtil.resizeImageRef(queryImage.get(), cw, ch);
				}
				
				SoftReference<BufferedImage> sceneImage = getCloneDetectImage();
				if(sceneImage != null) {
					l = ImageRecognition.findImage(cropImage.get(), sceneImage.get(), similarity);
					if(l != null) {
						int crw = cropImage.get().getWidth();
						int crh = cropImage.get().getHeight();
						cropImage = ImageUtil.cropImageRef(sceneImage.get(), l.getX(), l.getY(), crw, crh);
						if(cropImage != null) {
							l.setQueryImage(cropImage);
							l.setShotImage(sceneImage);
							l.encodeBase64Image();
						}
						if(sceneImage != null)
							sceneImage.clear();
						break;
					}
					sceneImage.clear();
				}
				
				for(int i=0;i<cnt;i++) {
					if(i == 0) {
						x = startx;
						y = starty;
						addTouchEventDown(true, (int)x, (int)y);
					}
					else if(i == cnt-1) {
						x = destx;
						y = desty;
						addTouchEventMove(true, (int)x, (int)y);
						addTouchEventUp(true);
						break;
					}
					else {
						x = x+stepx;
						y = y+stepy;
						
						addTouchEventMove(true, (int)x, (int)y);
						addTouchEventWait(ms);
						Thread.sleep(ms);
					}
				}
				
				end = System.currentTimeMillis();
				
				AnsiLog.d("[" + mDeviceModel + "] " + "Next Auto Swiping : " + (j+1) + ", (end-mLastChangeTime): " + (end-mLastChangeTime));
				AnsiLog.d("[" + mDeviceModel + "] " + "(end-mUnchangeTime) : " + (end-mUnChangeTime));
				if(mUnChangeTime > 0 && (end-mUnChangeTime) > min_unchange) {
					AnsiLog.w("[" + mDeviceModel + "] " + "Not Change Screen Auto Swiping: " + min_unchange);
					break;
				}
				Thread.sleep(wait);
			}
			
			if(l != null && method.compareToIgnoreCase("autotap") == 0) {
				x = l.getCX();
				y = l.getCY();
				doActionTouchEvent(wrap, method, (int)x, (int)y);
			}
		
		} catch(JSONException e) {
		} catch(InterruptedException e) {
		}
		return l;
	}
	
	public void doActionGestureEvent(CommandServer.ConnectionCommandWrap wrap, String method, JSONObject args) {
		boolean drag = method.compareToIgnoreCase("drag") == 0;
		boolean hold = drag && isTouchHold;
		if(isTouchHold) {
			mExecutorHold.shutdownNow();
			isTouchHold = false;
		}

		try {
			JSONObject json = null;
			int startx, starty, destx, desty, duration;
			int cnt;
			double stepx, stepy;
			int ms, swipe_limit;
			double x = 0, y = 0;
			
			startx = args.getInt("startx");
			starty = args.getInt("starty");
			destx = args.getInt("destx");
			desty = args.getInt("desty");
			duration = args.getInt("duration");
			json = getCalcSwipe(wrap, startx, starty, destx, desty, duration);
			
			ms = json.getInt("ms");
			swipe_limit = json.getInt("swipe_limit");
			cnt = json.getInt("cnt");
			startx = json.getInt("startx");
			starty = json.getInt("starty");
			destx = json.getInt("destx");
			desty = json.getInt("desty");
			duration = json.getInt("duration");
			stepx = json.getDouble("stepx");
			stepy = json.getDouble("stepy");
			
			while(!mTouchQueue.isEmpty()) {
				try { Thread.sleep(100); } catch(InterruptedException e) {}
			}

			for(int i=0;i<cnt;i++) {
				if(isCancelSwipe) {
					AnsiLog.i("[" + mDeviceModel + "] " + "Cancel Swiping or Dragging");
					break;
				}
				if(i == 0) {
					x = startx;
					y = starty;
					if(!hold)
						addTouchEventDown(true, (int)x, (int)y);
					else
						addTouchEventMove(true, (int)x, (int)y);
				}
				else if(i == cnt-1) {
					x = destx;
					y = desty;
					addTouchEventMove(true, (int)x, (int)y);
					addTouchEventUp(true);
					break;
				}
				else {
					x = x+stepx;
					y = y+stepy;
					
					addTouchEventMove(true, (int)x, (int)y);
					if(ms > 2 && (drag || i < cnt-swipe_limit)) {
						addTouchEventWait(ms);
						Thread.sleep(0);
					}
				}
			}
		} catch(JSONException e) {
		} catch(InterruptedException e) {
		}
	}
	
	public void doActionTouchEvent(CommandServer.ConnectionCommandWrap wrap, String method, int x, int y) {
		if(isTouchHold) {
			mExecutorHold.shutdownNow();
			isTouchHold = false;
		}
		if(method.compareToIgnoreCase("tap") == 0 || method.compareToIgnoreCase("autotap") == 0 || method.compareToIgnoreCase("click") == 0) {
			addEventTap(x, y);
		}
		else if(method.compareToIgnoreCase("doubletap") == 0 || method.compareToIgnoreCase("doubleclick") == 0) {
			addEventTap(x, y);
			//try { Thread.sleep(100); } catch(InterruptedException e) {}
			addTouchEventWait(100);
			addEventTap(x, y);
		}
		else if(method.compareToIgnoreCase("hold") == 0 || method.compareToIgnoreCase("longclick") == 0) {
			addTouchEventDown(true, x, y);
			//try { Thread.sleep(1000); } catch(InterruptedException e) {}
			addTouchEventWait(1000);
			isTouchHold = true;
			mExecutorHold.schedule(
				new Runnable() {
					public void run() {
						addTouchEventUp(true);
						isTouchHold = false;
					}
				}, 
				10, TimeUnit.SECONDS);
			//addTouchEventUp(true);
		}
		else if(method.compareToIgnoreCase("touchdown") == 0) {
			addTouchEventDown(true, x, y);
		}
		else if(method.compareToIgnoreCase("touchmove") == 0) {
			addTouchEventMove(true, x, y);
		}
		else if(method.compareToIgnoreCase("touchup") == 0) {
			addTouchEventUp(true);
		}
	}
	
	public ImageLocation doActionTouchEvent(CommandServer.ConnectionCommandWrap wrap, String method, JSONObject args, SoftReference<BufferedImage> queryImage) {
		ImageLocation l = null;
		Window window = wrap.getBaseDisplay();
		
		int tx = -1, ty = -1;
		double regionw = TouchScript.DEFAULT_REGION_W;
		double regionh = TouchScript.DEFAULT_REGION_H;
		double similarity = TouchScript.DEFAULT_SIMILARITY;
		boolean tap_coord_not_found = true;
		
		int x = -1, y = -1, cx = -1, cy = -1, cw = 0, ch = 0;
		int qx = queryImage.get().getWidth(), qy = queryImage.get().getHeight();
//		int ncw = getWindow().getCurrentWidth(), nch = getWindow().getCurrentHeight();
//		int ocw = window.getCurrentWidth(), och = window.getCurrentHeight();
		int nvw = getWindow().getVirtualWidth(), nvh = getWindow().getVirtualHeight();
		int ovw = window.getVirtualWidth(), ovh = window.getVirtualHeight();
		if((ovw < ovh && nvw > nvh) || (ovw > ovh && nvw < nvh)) {
			int t = nvh;
			nvh = nvw;
			nvw = t;
		}
		double rzX = (double)nvw/ovw;
		double rzY = (double)nvh/ovh;
		if(nvw < nvh && nvw == ovw)
			rzY = rzX;
		else if(nvw > nvh && nvh == ovh)
			rzX = rzY;
		
//		double nDpiX = getWindow().getDpiX() * ((double)nvw/ncw);
//		double nDpiY = getWindow().getDpiY() * ((double)nvh/nch);
//		double oDpiX = window.getDpiX() * ((double)ovw/ocw);
//		double oDpiY = window.getDpiY() * ((double)ovh/och);
//		
//		double rzX = nDpiX/oDpiX;
//		double rzY = nDpiY/oDpiY;
		
		if(args.has("cx")) {
			try { tx = args.getInt("cx"); } catch(JSONException e) {}
		}
		if(args.has("cy")) {
			try { ty = args.getInt("cy"); } catch(JSONException e) {}
		}
		if(args.has("regionw")) {
			try { regionw = args.getDouble("regionw"); } catch(JSONException e) {}
		}
		if(args.has("regionh")) {
			try { regionh = args.getDouble("regionh"); } catch(JSONException e) {}
		}
		if(args.has("similarity")) {
			try { similarity = args.getDouble("similarity"); } catch(JSONException e) {}
		}
		if(args.has("tap_coord_not_found")) {
			try { tap_coord_not_found = args.getBoolean("tap_coord_not_found"); } catch(JSONException e) {}
		}
		
		if(tx >= 0 && ty >= 0) {
			cx = (int)(rzX*tx);
			cy = (int)(rzY*ty); 
		}
		
		SoftReference<BufferedImage> cropImage = queryImage;
		if(rzX != 1.000f || rzY != 1.000f) {
			cw = (int)(rzX*qx);
			ch = (int)(rzY*qy);
			cropImage = ImageUtil.resizeImageRef(queryImage.get(), cw, ch);
		}
		
		SoftReference<BufferedImage> sceneImage = getCloneDetectImage();
		if(sceneImage != null) {
			if(tx >= 0 && ty >= 0)
				l = ImageRecognition.findImage(cropImage.get(), sceneImage.get(), similarity, cx, cy, regionw, regionh);
			if(l == null)
				l = ImageRecognition.findImage(cropImage.get(), sceneImage.get(), similarity);
			if(l != null) {
				x = l.getCX();
				y = l.getCY();
				l.setQueryImage(cropImage);
				l.setShotImage(sceneImage);
				l.encodeBase64Image();
				AnsiLog.d(String.format("[" + mDeviceModel + "] " + "n: %d-%d, === o: %d-%d, rz: %.3f-%.3f, c:%d-%d, r:%d-%d ", 
						nvw, nvh, 
						ovw, ovh, 
						rzX, rzY, 
						cx, cy, cw, ch));
	//			AnsiLog.d(String.format("[" + mDeviceModel + "] " + "n: %d-%d, %d-%d, d: %.3f-%.3f === o: %d-%d, %d-%d, d: %.3f-%.3f, rz: %.3f-%.3f, c:%d-%d, r:%d-%d ", 
	//					ncw, nch, nvw, nvh, nDpiX, nDpiY, 
	//					ocw, och, ovw, ovh, oDpiX, oDpiY, 
	//					rzX, rzY, 
	//					cx, cy, cw, ch));
			}
			else {
				if(tap_coord_not_found && tx >= 0 && ty >= 0) {
					x = cx;
					y = cy; 
					l = new ImageLocation();
					l.setCenter(new Point(x, y));
				}
			}
			sceneImage.clear();
		}
		
		if(x >= 0 && y >= 0) {
			doActionTouchEvent(wrap, method, x, y);
		}
		
		if(cropImage != queryImage)
			cropImage.clear();
		
		return l;
	}
	
	private JSONObject getCalcFind(CommandServer.ConnectionCommandWrap wrap) {
		JSONObject json = null;
		Window window = wrap.getBaseDisplay();
//		int ncw = getWindow().getCurrentWidth(), nch = getWindow().getCurrentHeight();
//		int ocw = window.getCurrentWidth(), och = window.getCurrentHeight();
		int nvw = getWindow().getVirtualWidth(), nvh = getWindow().getVirtualHeight();
		int ovw = window.getVirtualWidth(), ovh = window.getVirtualHeight();
		if((ovw < ovh && nvw > nvh) || (ovw > ovh && nvw < nvh)) {
			int t = nvh;
			nvh = nvw;
			nvw = t;
		}
		double rzX = (double)nvw/ovw;
		double rzY = (double)nvh/ovh;
		if(nvw < nvh && nvw == ovw)
			rzY = rzX;
		else if(nvw > nvh && nvh == ovh)
			rzX = rzY;
		
//		double nDpiX = getWindow().getDpiX() * ((double)nvw/ncw);
//		double nDpiY = getWindow().getDpiY() * ((double)nvh/nch);
//		double oDpiX = window.getDpiX() * ((double)ovw/ocw);
//		double oDpiY = window.getDpiY() * ((double)ovh/och);
//		
//		double rzX = nDpiX/oDpiX;
//		double rzY = nDpiY/oDpiY;

		try {
			json = new JSONObject();
			json.put("rzX", rzX);
			json.put("rzY", rzY);
		} catch(JSONException e) {}
		return json;
	}
	
	private JSONObject getCalcSwipe(CommandServer.ConnectionCommandWrap wrap, int startx, int starty, int destx, int desty, int duration) {
		JSONObject json = null;
		Window window = wrap.getBaseDisplay();
//		int ncw = getWindow().getCurrentWidth(), nch = getWindow().getCurrentHeight();
//		int ocw = window.getCurrentWidth(), och = window.getCurrentHeight();
		int nvw = getWindow().getVirtualWidth(), nvh = getWindow().getVirtualHeight();
		int ovw = window.getVirtualWidth(), ovh = window.getVirtualHeight();
		if((ovw < ovh && nvw > nvh) || (ovw > ovh && nvw < nvh)) {
			int t = nvh;
			nvh = nvw;
			nvw = t;
		}
		double rzX = (double)nvw/ovw;
		double rzY = (double)nvh/ovh;
		if(nvw < nvh && nvw == ovw)
			rzY = rzX;
		else if(nvw > nvh && nvh == ovh)
			rzX = rzY;
		
//		double nDpiX = getWindow().getDpiX() * ((double)nvw/ncw);
//		double nDpiY = getWindow().getDpiY() * ((double)nvh/nch);
//		double oDpiX = window.getDpiX() * ((double)ovw/ocw);
//		double oDpiY = window.getDpiY() * ((double)ovh/och);
//		
//		double rzX = nDpiX/oDpiX;
//		double rzY = nDpiY/oDpiY;
		
		if(rzX != 1.00f) {
			startx = (int)(rzX*startx);
			destx = (int)(rzX*destx);
		}
		if(rzY != 1.00f) {
			starty = (int)(rzY*starty);
			desty = (int)(rzY*desty);
		}

		int ms = 2, swipe_limit = 4;
		double stepx = 0, stepy = 0;
		int dx = Math.abs(destx-startx), dy = Math.abs(desty-starty);
		int dl = Math.max(dx, dy);
		int cnt;
		
		while((duration/ms)*2 > dl) {
			ms += 1; 
		}
		
		cnt = duration / ms;
		cnt += duration % ms > 0 ? 1 : 0;
		swipe_limit = (int)((double)cnt*0.1f);
		if(swipe_limit < 4)
			swipe_limit = 4;
		
		stepx = ((double)destx-startx)/(double)cnt;
		stepy = ((double)desty-starty)/(double)cnt;
		
		AnsiLog.d(String.format("[" + mDeviceModel + "] " + "ms:%d, cnt:%d, s:%d-%d(%d-%d), d:%d-%d(%d-%d), dis:%d, dur:%d, st:%.3f-%.3f", 
				ms, cnt, 
				startx, starty, TX(startx, starty), TY(startx, starty), 
				destx, desty, TX(destx, desty), TY(destx, desty),
				dl, duration, stepx, stepy));
			
		try {
			json = new JSONObject();
			json.put("startx", startx);
			json.put("starty", starty);
			json.put("destx", destx);
			json.put("desty", desty);
			json.put("stepx", stepx);
			json.put("stepy", stepy);
			json.put("rzX", rzX);
			json.put("rzY", rzY);
			json.put("duration", duration);
			json.put("cnt", cnt);
			json.put("ms", ms);
			json.put("swipe_limit", swipe_limit);
		} catch(JSONException e) {}
		return json;
	}
	
	private int CX(int x, int y, int w, int h) {
		int r = mWindow.getRotation();
		int v = 0;
		switch(r) {
		case 0:
			v = x;
			break;
		case 1:
			v = w-y;
			break;
		case 2:
			v = w-x;
			break;
		case 3:
			v = y;
			break;
		}
		return v;
	}
	
	private int CY(int x, int y, int w, int h) {
		int r = mWindow.getRotation();
		int v = 0;
		switch(r) {
		case 0:
			v = y;
			break;
		case 1:
			v = x;
			break;
		case 2:
			v = h-y;
			break;
		case 3:
			v = h-x;
			break;
		}
		return v;
	}

	public void addTouchEventDown(boolean commit, int x, int y, int w, int h) {
		int r = mWindow.getRotation();
		int cw = r%2==0 ? w : h;
		int ch = r%2==0 ? h : w;
		int cx = CX(x, y, cw, ch);
		int cy = CY(x, y, cw, ch);
		int sw = mTouch.getMaxX();
		int sh = mTouch.getMaxY();
		float rw = (float)sw / (float)cw;
		float rh = (float)sh / (float)ch;
		String cmd = "d 0", cmdl;
		
		int zx = (int)(rw*(float)cx);
		int zy = (int)(rh*(float)cy);
		cmd += " "; cmd += zx; cmd += " "; cmd += zy; cmd += " 50"; cmd += "\n";
		if(commit)
			cmd += "c\n";
		cmdl = cmd;

		AnsiLog.d("[" + mDeviceModel + "] " + "touch down cmd : " + cmdl.replaceAll("\n", " >>> "));
		try {
			mTouchQueue.add(cmd.getBytes("US-ASCII"));
			setTouchDown(true);
		} catch(UnsupportedEncodingException e) {}
	}

	public void addTouchEventDown(boolean commit, int x, int y) {
		int w = getWindow().getVirtualWidth(), h = getWindow().getVirtualHeight();
		addTouchEventDown(commit, x, y, w, h);
	}
	
	public void addTouchEventUp(boolean commit) {
		String cmd = "u 0\n", cmdl;
		if(commit)
			cmd += "c\n";
		cmdl = cmd;
		
		AnsiLog.d("[" + mDeviceModel + "] " + "touch up cmd : " + cmdl.replaceAll("\n", " >>> "));
		try {
			mTouchQueue.add(cmd.getBytes("US-ASCII"));
			setTouchDown(false);
		} catch(UnsupportedEncodingException e) {}
	}
	
	public void addTouchEventWait(int wait) {
		String cmd = "w " + wait + "\n", cmdl;
		cmdl = cmd;
		//AnsiLog.d("[" + mDeviceModel + "] " + "touch wait cmd : " + cmdl.replaceAll("\n", " >>> "));
		try {
			mTouchQueue.add(cmd.getBytes("US-ASCII"));
		} catch(UnsupportedEncodingException e) {}
	}
	
	public void addTouchEventMove(boolean commit, int x, int y, int w, int h) {
		int r = mWindow.getRotation();
		int cw = r%2==0 ? w : h;
		int ch = r%2==0 ? h : w;
		int cx = CX(x, y, cw, ch);
		int cy = CY(x, y, cw, ch);
		int sw = mTouch.getMaxX();
		int sh = mTouch.getMaxY();
		float rw = (float)sw / (float)cw;
		float rh = (float)sh / (float)ch;
		String cmd = "m 0", cmdl;
		
		int zx = (int)(rw*(float)cx);
		int zy = (int)(rh*(float)cy);
		cmd += " "; cmd += zx; cmd += " "; cmd += zy; cmd += " 50"; cmd += "\n";
		if(commit)
			cmd += "c\n";
		cmdl = cmd;
		//AnsiLog.d("[" + mDeviceModel + "] " + "touch move cmd : " + cmdl.replaceAll("\n", " >>> "));
		try {
			mTouchQueue.add(cmd.getBytes("US-ASCII"));
		} catch(UnsupportedEncodingException e) {}
	}
	
	public void addTouchEventMove(boolean commit, int x, int y) {
		int w = getWindow().getVirtualWidth(), h = getWindow().getVirtualHeight();
		addTouchEventMove(commit, x, y, w, h);
	}
	
	public void addEventTap(int x, int y, int w, int h) {
		addTouchEventDown(true, x, y, w, h);
		try { Thread.sleep(100); } catch(Exception e) {}
		addTouchEventUp(true);
	}
	
	public void addEventTap(int x, int y) {
		int w = getWindow().getVirtualWidth(), h = getWindow().getVirtualHeight();
		addEventTap(x, y, w, h);
	}
	
	public void addEventDrag(List<TouchActor> xy, int w, int h) {
		if(xy.isEmpty())
			return;
		
		TouchActor ta = xy.get(0);
		addTouchEventDown(true, ta.x, ta.y, w, h);
		if(ta.ms > 0)
			addTouchEventWait(ta.ms);
		for(int i=1;i<xy.size();i++) {
			ta = xy.get(i);
			addTouchEventMove(true, ta.x, ta.y, w, h);
			if(ta.ms > 0)
				addTouchEventWait(ta.ms);
		}
		addTouchEventUp(true);
	}
	
	public int TX(int x, int y) {
		int w = getWindow().getVirtualWidth(), h = getWindow().getVirtualHeight();
		int r = mWindow.getRotation();
		int cw = r%2==0 ? w : h;
		int ch = r%2==0 ? h : w;
		int cx = CX(x, y, cw, ch);
		int sw = mTouch.getMaxX();
		float rw = (float)sw / (float)cw;
		
		int zx = (int)(rw*(float)cx);
		
		return zx;
	}
	
	public int TY(int x, int y) {
		int w = getWindow().getVirtualWidth(), h = getWindow().getVirtualHeight();
		int r = mWindow.getRotation();
		int cw = r%2==0 ? w : h;
		int ch = r%2==0 ? h : w;
		int cy = CY(x, y, cw, ch);
		int sh = mTouch.getMaxY();
		float rh = (float)sh / (float)ch;
		
		int zy = (int)(rh*(float)cy);
		
		return zy;
	}
	
	public int PX(int x) {
		int cw = mWindow.getVirtualWidth();
		int ch = mWindow.getVirtualHeight();
		int sw = cw < ch ? mWindow.getPhysicalWidth() : mWindow.getPhysicalHeight();
		float rw = (float)sw / (float)cw;
		
		int zx = (int)(rw*(float)x);
		return zx;
	}
	
	public int PY(int y) {
		int cw = mWindow.getVirtualWidth();
		int ch = mWindow.getVirtualHeight();
		int sh = cw < ch ? mWindow.getPhysicalHeight() : mWindow.getPhysicalWidth();
		float rh = (float)sh / (float)ch;
		
		int zy = (int)(rh*(float)y);
		return zy;
	}
	
	public void sendDeviceKeyEvent(String sKey) {
		String cmd = "";
		int idx = -1;
		
		for(int i=0;i<mKeyDevices.length;i++) {
			if(sKey.compareToIgnoreCase(mKeyNames[i]) == 0 && !mKeyDevices[i].isEmpty()) {
				idx = i;
				cmd = String.format(KEYEVENT_DOWN_COMMAND, mKeyDevices[i], mKeyCodes[i]);
				cmd += String.format(KEYEVENT_COMMIT_COMMAND, mKeyDevices[i]);
				cmd += String.format(KEYEVENT_UP_COMMAND, mKeyDevices[i], mKeyCodes[i]);
				cmd += String.format(KEYEVENT_COMMIT_COMMAND, mKeyDevices[i]);
				break;
			}
		}
		
//		if(cEvent.isEmpty()) {
//			if(sKey.contentEquals("KEYCODE_MENU") && mKeyDevices[KEYC_MENU].isEmpty() && !mKeyDevices[KEYC_BACK].isEmpty()) {
//				int i = KEYC_BACK;
//				cEvent = String.format(KEYEVENT_DOWN_COMMAND, mKeyDevices[i], mKeyCodes[i]);
//				cEvent += String.format(KEYEVENT_COMMIT_COMMAND, mKeyDevices[i]);
//				cEvent += "sleep 1\n";
//				cEvent += String.format(KEYEVENT_UP_COMMAND, mKeyDevices[i], mKeyCodes[i]);
//				cEvent += String.format(KEYEVENT_COMMIT_COMMAND, mKeyDevices[i]);
//			}
//		}
		
		if(cmd.isEmpty()) {
			cmd = "input keyevent " + sKey;
		}
		
		AnsiLog.d("[" + mDeviceModel + "] " + "keyEvent : " + cmd);
		executeShellCommand(cmd);
	}
	
	public boolean sendWake() {
		sendDeviceKeyEvent("KEYCODE_WAKEUP");
		return true;
	}
	
	public void executeSensor(boolean sensor) {
		if(mWindow.getSensor() != sensor) {
			String cmd = String.format("settings put system accelerometer_rotation %d", sensor ? 1 : 0);
			AnsiLog.d("[" + mDeviceModel + "] " + cmd);
			executeShellCommand(cmd);
		}
	}
	
	public void executeRotation(int rotation) {
		String cmd = "";
		boolean rotating = false;
		if(mWindow.getSensor() != false) {
			if(!cmd.isEmpty()) cmd += " && ";
			cmd += "settings put system accelerometer_rotation 0";
		}
		if(mWindow.getRotation() != rotation) {
			rotating = true;
			if(!cmd.isEmpty()) cmd += " && ";
			cmd += String.format("settings put system user_rotation %d", rotation);
		}
		if(!cmd.isEmpty()) {
			AnsiLog.d("[" + mDeviceModel + "] " + cmd);
			executeShellCommand(cmd);
			
			if (rotating) {
	            initWindow();
			}
		}
	}
	
	public void executeRotation(String sRotation) {
		for(int i=0;i<mRotationNames.length;i++) {
			if(sRotation.compareToIgnoreCase(mRotationNames[i]) == 0) {
				if(mRotationValues[i] == -1)
					executeSensor(!mWindow.getSensor());
				else
					executeRotation(mRotationValues[i]);
				break;
			}
		}
	}
	
	public void executeOrientation(String sOrientation) {
		for(int i=0;i<mRotationNames.length;i++) {
			if(sOrientation.compareToIgnoreCase(mOrientationNames[i]) == 0) {
				if(mOrientationValues[i] == -1) {
					if(mWindow.getRotation() == 0)
						executeRotation(1);
					else
						executeRotation(0);
				}
				else
					executeRotation(mOrientationValues[i]);
				break;
			}
		}
	}
	
	public int getRotationValue(String sRotation) {
		for(int i=0;i<mRotationNames.length;i++) {
			if(sRotation.compareToIgnoreCase(mRotationNames[i]) == 0) {
				return mRotationValues[i];
			}
		}
		return 0;
	}
	
	public void setRotation(int rotation) {
		if(mWindow.getRotation() != rotation) {
			AnsiLog.i(1020, "current rotation: " + mWindow.getRotation() + ", new rotation: " + rotation);
			mWindow.setRotation(rotation);
			initWindow();
		}
	}
	
	public JSONObject getResourceStatus(String packageApp, int topCount, boolean topAboveZero) {
		JSONObject jsonRoot = new JSONObject();
		JSONObject jsonData = new JSONObject();
		
		try {
			Pattern p;
			Matcher m;
			String output;
		
			jsonRoot.put("method", "monitor");
			jsonData.put("method", "monitor");
			jsonData.put("name", mDeviceName);
			jsonData.put("serial", mDeviceSerial);
			jsonData.put("model", mDeviceModel);
			
			{
				String[] keys = {"cpu_total_percent", "cpu_user_percent", "cpu_kernel_percent"};
				for(int j=0;j<keys.length;j++)
					jsonData.put(keys[j], 0);
				
				output = executeShellCommand("dumpsys cpuinfo | grep -E \"TOTAL\"").trim();
				p = Pattern.compile("([\\.0-9]+)% TOTAL: ([\\.0-9]+)% user \\+ ([\\.0-9]+)% kernel");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 3) { // groupCount bug???(+1)
						try {
							for(int j=0;j<keys.length;j++)
								jsonData.put(keys[j], Double.valueOf(m.group(j+1)));
						} catch(NumberFormatException e) {}
					}
				}
			}
			
			{
				String[] keys = {"mem_total_kbyte", "mem_free_kbyte", "mem_cached_kbyte", "mem_swapcached_kbyte"};
				for(int j=0;j<keys.length;j++)
					jsonData.put(keys[j], 0);

				output = executeShellCommand("cat /proc/meminfo").trim();
				if(!output.isEmpty()) {
					String[] rows = output.split("\\r?\\n");
					String[] names = {"MemTotal: ", "MemFree: ", "Cached: ", "SwapCached: "};
					for(int i=0;i<rows.length;i++) {
						String row = rows[i].trim();
						for(int j=0;j<names.length;j++) {
							if(row.startsWith(names[j])) {
								p = Pattern.compile(names[j] + " +([0-9]+) kB");
								m = p.matcher(row);
								if(m.find()) {
									if(m.groupCount() == 1) { // groupCount bug???(+1)
										try {
											jsonData.put(keys[j], Long.valueOf(m.group(1)));
										} catch(NumberFormatException e) {}
									}
								}
							}
						}
					}
				}
			}
			
			if(topCount > 0) {
				output = executeShellCommand("top -n 1 -m " + topCount).trim();
				if(!output.isEmpty()) {
					String[] rows = output.split("\\r?\\n");
					String[] exceptProcs = {"top", "/data/local/tmp/mirroring/mirror", "/data/local/tmp/mirroring/mirrortouch", "system_server"}; 
					
					List<String> pidNames = new ArrayList<String>();
					JSONArray jsonPids = new JSONArray();
					for(int i=0;i<rows.length;i++) {
						String row = rows[i].trim();
						String sName = "";
						if(pidNames.isEmpty() && row.startsWith("PID")) {
							String[] cols = row.split("[ ]+");
							for(int j=0;j<cols.length;j++) {
								if(cols[j].compareTo("CPU%") == 0)
									pidNames.add("CPU");
								else if(cols[j].compareTo("S") == 0)
									pidNames.add("STATE");
								else if(cols[j].compareTo("#THR") == 0)
									pidNames.add("THREAD");
								else if(cols[j].compareTo("Name") == 0)
									pidNames.add("NAME");
								else
									pidNames.add(cols[j]);
							}
						}
						else if(!pidNames.isEmpty()) {
							String[] cols = row.split("[ ]+");
							JSONObject jsonPid = new JSONObject();
							int len = Math.min(cols.length, pidNames.size());
							List<String> pidNames2 = pidNames;
							if(cols.length == pidNames.size()-1) {
								pidNames2 = new ArrayList<String>();
								pidNames2.addAll(pidNames);
								pidNames2.remove("PCY");
							}
							for(int j=0;j<len;j++) {
								if(pidNames2.get(j).compareTo("CPU") == 0) {
									cols[j] = cols[j].replaceAll("\\%", "");
								}
								else if(pidNames2.get(j).compareTo("STATE") == 0) {
									if(cols[j].compareTo("S") == 0)
										cols[j] = "SLEEP";
									else if(cols[j].compareTo("R") == 0)
										cols[j] = "RUN";
								}
								else if(pidNames2.get(j).compareTo("NAME") == 0) {
									sName = cols[j].toLowerCase();
								}
								jsonPid.put(pidNames2.get(j), cols[j]);
							}
							
							boolean skip = false;
							for(int k=0;k<exceptProcs.length;k++) {
								if(sName.compareTo(exceptProcs[k]) == 0) {
									skip = true;
									break;
								}
							}
							
							if(!skip) {
								if(!topAboveZero || jsonPid.getString("CPU").compareTo("0") != 0)
									jsonPids.put(jsonPid);
								else
									break;
							}
						}
					}
					if(jsonPids.length() > 0)
						jsonData.put("top", jsonPids);
				}
			}
			
			if(packageApp != null && !packageApp.isEmpty()) {
				JSONObject jsonPkg = new JSONObject();
			
				jsonData.put("package", packageApp);
				
				{
					String[] keys = {"cpu_total_percent", "cpu_user_percent", "cpu_kernel_percent"};
					for(int j=0;j<keys.length;j++)
						jsonPkg.put(keys[j], 0);
					
					output = executeShellCommand("dumpsys cpuinfo | grep -E \"" + packageApp + "\"").trim();
					p = Pattern.compile("([\\.0-9]+)% .+([\\.0-9]+)% .+([\\.0-9]+)%");
					m = p.matcher(output);
					if(m.find()) {
						if(m.groupCount() == 3) { // groupCount bug???(+1)
							try {
								for(int j=0;j<keys.length;j++)
									jsonPkg.put(keys[j], Double.valueOf(m.group(j+1)));
							} catch(NumberFormatException e) {}
						}
					}
				}
				
				jsonPkg.put("mem_total_byte", 0);
				output = executeShellCommand("dumpsys meminfo \"" + packageApp + "\" | grep -E \"TOTAL\"").trim();
				p = Pattern.compile("TOTAL +([\\.0-9]+)");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try {
							jsonPkg.put("mem_total_byte", Integer.valueOf(m.group(1)));
						} catch(NumberFormatException e) {}
					}
				}
				jsonData.put("app", jsonPkg);
			}
			jsonRoot.put("success", true);
			jsonRoot.put("data", jsonData);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return jsonRoot;
	}

	public void setFinalImage(SoftReference<BufferedImage> image) {
		SoftReference<BufferedImage> cloneImage = null; 
		if(image != null)
			cloneImage = ImageUtil.cloneImageRef(image.get());
		synchronized(mSyncDetect) {
			if(mFinalImage != null)
				mFinalImage.clear();
			mFinalImage = cloneImage;
			mLastChangeTime = System.currentTimeMillis();
		}
	}
	
	public SoftReference<BufferedImage> getFinalImage() {
		synchronized(mSyncDetect) {
			return mFinalImage;
		}
	}
	
	public SoftReference<BufferedImage> getCloneFinalImage() {
		synchronized(mSyncDetect) {
			if(mFinalImage != null)
				return ImageUtil.cloneImageRef(mFinalImage.get());
			return null;
		}
	}

	public void setDetectImage(SoftReference<BufferedImage> image) {
		SoftReference<BufferedImage> cloneImage = null; 
		if(image != null)
			cloneImage = ImageUtil.cloneImageRef(image.get());
		synchronized(mSyncDetect) {
			if(mDetectImage != null)
				mDetectImage.clear();
			mDetectImage = cloneImage;
		}
	}
	
	public SoftReference<BufferedImage> getDetectImage() {
		synchronized(mSyncDetect) {
			return mDetectImage;
		}
	}
	
	public SoftReference<BufferedImage> getCloneDetectImage() {
		synchronized(mSyncDetect) {
			if(mDetectImage != null)
				return ImageUtil.cloneImageRef(mDetectImage.get());
			return null;
		}
	}
	
	public SoftReference<BufferedImage> getCropDetectImage(int x, int y, int crw, int crh) {
		if(getDetectImage() == null)
			return null;
		
		return ImageUtil.cropImageRef(getDetectImage().get(), x, y, crw, crh);
	}
	
	public SoftReference<BufferedImage> getCenterCropDetectImage(int cx, int cy, int crw, int crh) {
		if(getDetectImage() == null)
			return null;
		
		int x, y;
		int dw, dh;
		dw = crw/2; dw += crw%2;
		dh = crh/2; dh += crh%2;
		
		x = cx-dw; if(x < 0) x = 0;
		y = cy-dh; if(y < 0) y = 0;
		
		return getCropDetectImage(x, y, crw, crh);
	}
	
	public void setCancelSwipe(boolean cancel) {
		isCancelSwipe = cancel;
	}
	
	public void setCancelAutoSwipe(boolean cancel) {
		isCancelAutoSwipe = cancel;
	}

	public void setCancelFind(boolean cancel) {
		isCancelFind = cancel;
	}

	public static String executeShellCommand(IDevice dev, String command, CollectingOutputReceiver outputReceiver) {
		try {
			dev.executeShellCommand(command, outputReceiver, 0);
		} catch (TimeoutException e) {
			//e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			//e.printStackTrace();
		} catch (ShellCommandUnresponsiveException e) {
			//e.printStackTrace();
		} catch (IOException e) {
			//e.printStackTrace();
		}
		return outputReceiver.getOutput();
	}
	
	public static String executeShellCommand(IDevice dev, String command) {
		if(dev == null)
			return "";
		
		CollectingOutputReceiver output = new CollectingOutputReceiver();
		return executeShellCommand(dev, command, output);
	}

	protected String executeShellCommand(String command, CollectingOutputReceiver outputReceiver) {
		return executeShellCommand(mDevice, command, outputReceiver);
	}
		
	protected String executeShellCommand(String command) {
		return executeShellCommand(mDevice, command);
	}
	
	protected String installPackage(String apk, boolean reinstall) {
		String result = "Not Found APK File";
		File apkFile = new File(apk);
		if(apkFile != null && apkFile.exists()) {
			int cnt = 0;
			while(cnt < 2) {
				try {
					mDevice.installPackage(apkFile.getAbsolutePath(), true);
					result = "OK";
					cnt = 2;
				} catch (InstallException e) {
					result = e.toString();
					cnt++;
					if(cnt < 2) {
						String pkg = AndroidUtil.getApkPackageName(apk);
						if(pkg != null) {
							try {
								mDevice.uninstallPackage(pkg);
							} catch (InstallException eu) {
								result = eu.toString();
								break;
							}
						}
					}
				}
			}
		}
		return result;
	}
	
	protected String uninstallPackage(String pkg) {
		String result = "";
		String output = executeShellCommand("pm path " + pkg).trim();
		if(!output.isEmpty()) {
			output = executeShellCommand("pm list packages -s | grep " + pkg).trim();
			if(output.isEmpty()) {
				try {
					mDevice.uninstallPackage(pkg);
					result = "OK";
				} catch (InstallException e) {
					result = e.toString();
				}
			}
			else {
				result = "Not uninstall a System Package " + pkg;
			}
		}
		else {
			result = "Not Found a installed Package " + pkg;
		}
		return result;
	}
	
	protected String isInstallPackage(String pkg) {
		String output = executeShellCommand("pm path " + pkg).trim();
		return !output.isEmpty() ? "OK" : "Not Found installed " + pkg;
	}
	
	protected void runPackageApp(String pkg, String activity, String intent) {
		String cmd = "";
		if(activity != null && !activity.isEmpty()) {
			cmd = "am start -n " + pkg + "/" + activity;
			if(intent != null)
				cmd += " " + intent;
		}
		else {
			cmd = "monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1";
		}
		executeShellCommand(cmd);
	}
	
	protected void runPackageService(String pkg, String service, String intent) {
		String cmd = "am startservice --user 0 -n " + pkg + "/" + service;
		if(intent != null)
			cmd += " " + intent;
		executeShellCommand(cmd);
	}
	
	protected void stopPackageApp(String pkg) {
		String cmd = "am force-stop " + pkg;
		executeShellCommand(cmd);
	}
	
	protected void stopPackageService(String pkg, String service, String intent) {
		String cmd = "am stopservice --user -n " + pkg;
		if(intent != null)
			cmd += " " + intent;
		executeShellCommand(cmd);
	}
	
	protected JSONObject getPkgInfo(String pkg) {
		return AndroidUtil.getPkgInfo(mDevice, pkg);
	}
	
	protected JSONObject getTopActivity() {
		return AndroidUtil.getTopActivity(mDevice);
	}
	
	public String getDeviceModel() {
		return mDeviceModel;
	}
	
	public String getDeviceName() {
		return mDeviceName;
	}
	
	public String getDeviceSerial() {
		return mDeviceSerial;
	}
	
	public int getObserverCount() {
		return mObservers.size();
	}
	
	public void registerObserver(AndroidScreenObserver o) {
		int index = mObservers.indexOf(o);
		if (index == -1) {
			mObservers.add(o);
		}
	}

	public void removeObserver(AndroidScreenObserver o) {
		int index = mObservers.indexOf(o);
		if (index != -1) {
			mObservers.remove(o);
		}
	}

	private void notifyObservers(Window window) {
		for (AndroidScreenObserver observer : mObservers) {
			observer.initWindow(window);
		}
	}	

	private void notifyObservers(Banner banner) {
		for (AndroidScreenObserver observer : mObservers) {
			observer.initBanner(banner);
		}
	}	

	private void notifyObservers(SoftReference<BufferedImage> image) {
		//AnsiLog.d("[" + mDeviceModel + "] " + "frameImage w*h : " + image.get().getWidth() + ", " + image.get().getHeight());
		if(image == null)
			return;
		for (AndroidScreenObserver observer : mObservers) {
			observer.frameImage(image);
			//observer.frameImage(ImageUtil.cloneImageRef(image.get()));
		}
	}
	
	private void notifyReleaseObservers() {
		for (AndroidScreenObserver observer : mObservers) {
			observer.release();
		}
	}

	public static boolean isBigEndian() {
		return ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
	}
	
	public static byte[] toLittleEndianArray(int value){
	    ByteBuffer buffer = ByteBuffer.allocate(4);
    	buffer.order(ByteOrder.LITTLE_ENDIAN);
	    buffer.putInt(value);
	    buffer.flip();
	    return buffer.array();
	}

	private static SoftReference<byte[]> byteMerger(byte[] byte_1, byte[] byte_2) {
		SoftReference<byte[]> byte_3 = new SoftReference<byte[]>(new byte[byte_1.length + byte_2.length]);
		System.arraycopy(byte_1, 0, byte_3.get(), 0, byte_1.length);
		System.arraycopy(byte_2, 0, byte_3.get(), byte_1.length, byte_2.length);
		return byte_3;
	}

	private static SoftReference<byte[]> subByteArray(byte[] byte1, int start, int end) {
		if(end > start) {
			SoftReference<byte[]> byte2 = new SoftReference<byte[]>(new byte[end-start]);
			System.arraycopy(byte1, start, byte2.get(), 0, end - start);
			return byte2;
		}
		return new SoftReference<byte[]>(new byte[0]);
	}

	private static SoftReference<byte[]> copyByteArray(byte[] byte1) {
		SoftReference<byte[]> byte2 = new SoftReference<byte[]>(new byte[byte1.length]);
		System.arraycopy(byte1, 0, byte2.get(), 0, byte1.length);
		return byte2;
	}
	
	public static void encryptZip() {
		try {
			File mirrorDat = Constant.getMrrorDat();
			File mirrorZip = Constant.getMrrorZip();
			
			CryptoUtil.encrypt(Constant.MIRROR_KEY, mirrorZip, mirrorDat);
		} catch(CryptoException e) {
			//e.printStackTrace();
		}
	}
	
	public static void decryptZip() {
		try {
			File mirrorDat = Constant.getMrrorDat();
			File mirrorZip = Constant.getMrrorTestZip();
			
			CryptoUtil.decrypt(Constant.MIRROR_KEY, mirrorDat, mirrorZip);
		} catch(CryptoException e) {
			//e.printStackTrace();
		}
	}
}
