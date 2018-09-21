package com.hyundai.autoever.mirror.engine;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.IDevice.DeviceUnixSocketNamespace;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.utils.*;

public class MirrorEngine {
	private static final Logger LOG = Logger.getLogger(MirrorEngine.class);
	
	private static final String REMOTE_PATH = "/data/local/tmp/mirroring";
	private static final String MIRROR_BIN = "mirror";
	private static final String MIRROR_NP_BIN = "mirror-np";
	private static final String MIRROR_SO = "mirror.so";
	private static final String MIRRORTOUCH_BIN = "mirrortouch";
	private static final String MIRRORTOUCH_NP_BIN = "mirrortouch-np";
	
	private static final String AGENT_MIRROR_APK = "mirroragent.apk";
	private static final String KOR_KBD_APK = "hwkoreanime.apk";
	private static final String ADB_KBD_APK = "adbhwsoftime.apk";
	
	private static final String KOR_KBD_PKG = "com.bq.hwkoreanime";
	private static final String KOR_KBD_CLASS = "SoftKeyboard";
	private static final String KOR_KBD_IME = KOR_KBD_PKG + "/." + KOR_KBD_CLASS;
	//private static final String KOR_KBD_SOCKET = "bqKeyboardService";
	
	private static final String ADB_KBD_PKG = "com.bq.adbhwsoftime";
	private static final String ADB_KBD_CLASS = "SoftKeyboard";
	private static final String ADB_KBD_IME = ADB_KBD_PKG + "/." + ADB_KBD_CLASS;
	private static final String ADB_KBD_SOCKET = "bqAdbKeyboardService";
	
	private static final String AGENT_SERVICE_PKG = "com.hyundai.autoever.mirrorservice";
	private static final String AGENT_SERVICE_CLASS = "BootstrapTest";
	private static final String AGENT_SERVICE_METHOD = "startServer";
	private static final String AGENT_CONSOLE = "AgentConsole";
	private static final String AGENT_CONSOLE_NAME = "mirrorservice.agent";
	private static final String AGENT_SERVICE_SOCKET = "agentservice";
	
	private static final String MIRROR_MKDIR_COMMAND = "mkdir " + REMOTE_PATH;
	private static final String MIRROR_CHMOD_COMMAND = "chmod 744 " + REMOTE_PATH + "/" + "%s";
	private static final String MIRROR_DUMPSYS_WINDOW_COMMAND = "dumpsys window";
	private static final String MIRROR_DUMPSYS_DISPLAY_COMMAND = "dumpsys display";
	private static final String MIRROR_DUMPSYS_BATTERY_HEALTH_COMMAND = "dumpsys battery | grep -E \"health: \"";
	private static final String MIRROR_DUMPSYS_WIFI_COMMAND = "dumpsys connectivity | grep -E \"type: WIFI\\[.+ state: \"";
	private static final String MIRROR_PROC_MEMTOTAL_COMMAND = "cat proc/meminfo | grep -E \"MemTotal: \"";
	private static final String MIRROR_START_COMMAND = "LD_LIBRARY_PATH="+REMOTE_PATH+" "+REMOTE_PATH+"/"+MIRROR_BIN+" -S"+" -Q %d"+" -P %s@%s/%d";
	private static final String MIRRORTOUCH_START_COMMAND = REMOTE_PATH+"/"+MIRRORTOUCH_BIN;
	
	private static final String AGENT_GET_KBD_COMMAND = "settings get secure default_input_method";
	private static final String AGENT_GET_PATH_COMMAND = "pm path " + AGENT_SERVICE_PKG;
	private static final String AGENT_START_COMMAND = "export CLASSPATH=\"%s\"; exec app_process /system/bin " + AGENT_SERVICE_PKG + "." + AGENT_CONSOLE;
			
	private static final String AGENT_SERVICE_START_COMMAND = "am instrument -w -r -e debug false -e class " + AGENT_SERVICE_PKG + ".test." + AGENT_SERVICE_CLASS + "#" + AGENT_SERVICE_METHOD + " " + AGENT_SERVICE_PKG + "/android.support.test.runner.AndroidJUnitRunner";
	private static final String AGENT_SERVICE_STOP_COMMAND = "am force-stop " + AGENT_SERVICE_PKG;
	
	private static final String KOR_KBD_SET_COMMAND = "ime set " + KOR_KBD_IME; 
	private static final String ADB_KBD_SET_COMMAND = "ime set " + ADB_KBD_IME;
	
	private static final String MIRROR_TAKESCREENSHOT_COMMAND = "LD_LIBRARY_PATH="+REMOTE_PATH+" "+REMOTE_PATH+"/"+MIRROR_BIN+" -P %s@%s/%d -s > %s";
	private static final String ADB_PULL_COMMAND = "adb -s %s pull %s %s";
	private static final String MIRROR_VER_COMMAND = "touch " + REMOTE_PATH+"/%s";
	private static final String MIRROR_RM_VER_COMMAND = "rm -fr " + REMOTE_PATH+"/*.ver";
	private static final String MIRROR_LS_COMMAND = "ls " + REMOTE_PATH+"/%s";
	private static final String MIRROR_LS_ALL_COMMAND = "ls " + REMOTE_PATH+"/%s " + REMOTE_PATH+"/%s " + REMOTE_PATH+"/%s " + REMOTE_PATH+"/%s "; // mirror(bin), mirrortouch(bin), mirror(so), ver
	private static final String MIRROR_RM_COMMAND = "rm -fr " + REMOTE_PATH;
	private static final String MIRROR_PS_MIRROR_COMMAND = "ps | grep "+REMOTE_PATH+"/"+MIRROR_BIN;
	private static final String MIRROR_PS_MIRRORTOUCH_COMMAND = "ps | grep "+REMOTE_PATH+"/"+MIRROR_BIN;
	private static final String AGENT_CONSOLE_PS_APP_COMMAND = "ps | grep "+AGENT_CONSOLE_NAME;
	private static final String MIRROR_KILL_COMMAND = "kill ";

	private static final String GETEVENT_COMMAND = "getevent -p";
	
	private static final String GET_PKG_VER_COMMAND = "dumpsys package %s | grep versionCode";
	
	private final int[] mKeyCodes = new int[]{Constant.KEY_VOLUMEUP, Constant.KEY_VOLUMEDOWN, Constant.KEY_APP_SWITCH, Constant.KEY_HOMEPAGE, Constant.KEY_MENU, Constant.KEY_BACK, Constant.KEY_POWER, Constant.KEY_WAKEUP};
	private String[] mKeyDevices = new String[]{"","","","","","","",""};
	
	private int MIN_VIRTUAL_WH = 540;
	private int MIRROR_PORT = 1717;
	private int TOUCH_PORT = 1718;
	//private int KBD_PORT = 1719;
	private int AGENT_PORT = 1720;
	private int SERVICE_PORT = 1721;
	
	private String mDefaultKbd = null;
	
	private Window mWindow = new Window();
	private String mPhysicalSize = "", mVirtualSize = "";
	private volatile IDevice mDevice;
	private volatile boolean isRunning = false;
	
	private ConfigFile mCfg;
	
	private CollectingOutputReceiver mMirrorCmdAdb = null, mTouchCmdAdb = null, mServiceCmdAdb = null, mAgentCmdAdb = null;
	private static ExecutorService mEngineThreadPool = null;
	
	private String mDeviceName = "";
	private String mDeviceModel = "";
	private String mDeviceSerial = "";
	
	public MirrorEngine(IDevice dev) {
		mDevice = dev;
		mDeviceName = dev.getName();
		mDeviceModel = dev.getProperty(IDevice.PROP_DEVICE_MODEL);
		mDeviceSerial = dev.getSerialNumber();
		
		//AnsiLog.d("[" + mDeviceModel + "] " + "New ENGINE");
		
		mEngineThreadPool = Executors.newFixedThreadPool(4);
		
		mCfg = ConfigFile.get();
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
	
	public static void removeMirrorOfAndroid(ADB adb) {
		if(!adb.initDevices())
			return;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return;
		
		int len = adb.getDevices().length;
		AnsiLog.d(" " + "There are " + len + " Android connections.");
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			AnsiLog.i("The MIRROR-MODULE of Android(" + dev.getName() + ") is removing."); 
			executeShellCommand(dev, MIRROR_RM_COMMAND);
			
			try {
				dev.uninstallPackage(AGENT_SERVICE_PKG);
			} catch (InstallException e) {}
			try {
				dev.uninstallPackage(KOR_KBD_PKG);
			} catch (InstallException e) {}
			try {
				dev.uninstallPackage(ADB_KBD_PKG);
			} catch (InstallException e) {}
		}
		AnsiLog.d(" " + "The MIRROR-MODULE of Androids has benn Removed.");
	}
	
	public boolean init() {
		if(!initEngine())
			return false;
		initPort();
		initWindow();
		initKeyDeviceCode();
		
		initSaveConfig();
		return true;
	}

	private boolean initEngine() {
		boolean success = true;
		String abi = mDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI);
		String sdk = mDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL);
		
		// checking mirror bin/so/ver file of android
		String ls = String.format(MIRROR_LS_ALL_COMMAND, MIRROR_BIN, MIRRORTOUCH_BIN, MIRROR_SO, Constant.MIRROR_VER);
		String resultls = executeShellCommand(ls);
		if(!resultls.contains(": No ")) {
			AnsiLog.i2(1000, "[" + mDeviceModel + "] " + "The MIRROR-MODULE has been ALREADY INSTALLED on android.");
			return installApk();
		}

		executeShellCommand(MIRROR_MKDIR_COMMAND);
		String mirrorBinSource = "" , mirrorSoSource = "", mirrortouchBinSource = "";
		String mirrorBinDest = "" , mirrorSoDest = "", mirrortouchBinDest = "";
		try {
			if(Integer.valueOf(sdk) < 16) {
				mirrorBinSource = Constant.MIRROR_BIN + "/" + abi + "/" + MIRROR_NP_BIN;
				mirrorBinDest = MIRROR_NP_BIN;
				
				mirrortouchBinSource = Constant.MIRROR_BIN + "/" + abi + "/" + MIRRORTOUCH_NP_BIN;
				mirrortouchBinDest = MIRRORTOUCH_NP_BIN;
			}
		} catch(NumberFormatException e) {}

		if(mirrorBinSource.isEmpty()) {
			mirrorBinSource = Constant.MIRROR_BIN + "/" + abi + "/" + MIRROR_BIN;
			mirrorBinDest = MIRROR_BIN;
			
			mirrortouchBinSource = Constant.MIRROR_BIN + "/" + abi + "/" + MIRRORTOUCH_BIN;
			mirrortouchBinDest = MIRRORTOUCH_BIN;
		}
		mirrorSoSource = Constant.MIRROR_SO + "/" + "android-" + sdk + "/" + abi + "/" + MIRROR_SO;
		mirrorSoDest = MIRROR_SO;
		
		// checking mirror bin/so file of PC
		File mirrorVerFile = Constant.getTmpFile(Constant.MIRROR_VER);
		
		File mirrorDat = Constant.getMrrorDat();
		File mirrorZip = Constant.getMrrorTmpZip();
		if(!mirrorVerFile.exists() || !mirrorZip.exists()) {
			try {
				final File dir = Constant.getTmpDir();
				if(dir.exists()) {
				    final String[] allFiles = dir.list();
				    for (final String file : allFiles) {
				        if (file.endsWith(".ver")) {
				            File f = new File(dir.getAbsolutePath(), file);
				            f.delete();
				        }
				    }
				}
				else {
					dir.mkdirs();
				}
				CryptoUtil.decrypt(Constant.MIRROR_KEY, mirrorDat, mirrorZip);
			    mirrorVerFile.createNewFile();
			} catch (IOException e) {
				success = false;
			} catch (CryptoException e) {
				success = false;
			}
			
			if(!success) {
				AnsiLog.e(9200, "[" + mDeviceModel + "] " + "There was an error preparing1 to install the MIRROR-MODULE.");
				return false;
			}
		}
		
		File mirrorBinFile = Constant.getTmpBinFile(mirrorBinDest, abi);
		File mirrorSoFile = Constant.getTmpSoFile(mirrorSoDest, abi, sdk);
		File mirrortouchBinFile = Constant.getTmpBinFile(mirrortouchBinDest, abi);
		// extract mirror bin/so file at zip in PC
		if(mirrorZip.exists() && 
			(!mirrorBinFile.exists() ||
			 !mirrortouchBinFile.exists() || !mirrorSoFile.exists()))
		{
			File tmpBinDir = Constant.getTmpBinDir(abi);
			File tmpSoDir = Constant.getTmpSoDir(abi, sdk);
			
			try {
				CompressionUtil.unzipFile(mirrorZip, tmpBinDir, mirrorBinSource);
				CompressionUtil.unzipFile(mirrorZip, tmpSoDir, mirrorSoSource);
				CompressionUtil.unzipFile(mirrorZip, tmpBinDir, mirrortouchBinSource);
			} catch (IOException e) {
				success = false;
			}
			
//			if(mirrorZip != null && mirrorZip.exists())
//				mirrorZip.delete();
			
			if(!success) {
				AnsiLog.e(9200, "[" + mDeviceModel + "] " + "There was an error preparing2 to install the MIRROR-MODULE.");
				return false;
			}
		}
		
		// push mirror bin/so file to android from PC
		try {
			if(mirrorBinFile != null && mirrorBinFile.exists()) {
				mDevice.pushFile(mirrorBinFile.getAbsolutePath(), REMOTE_PATH + "/" + MIRROR_BIN);
				//mirrorBinFile.delete();
			}
			if(mirrorSoFile != null && mirrorSoFile.exists()) {
				mDevice.pushFile(mirrorSoFile.getAbsolutePath(), REMOTE_PATH + "/" + MIRROR_SO);
				//mirrorSoFile.delete();
			}
			if(mirrortouchBinFile != null && mirrortouchBinFile.exists()) {
				mDevice.pushFile(mirrortouchBinFile.getAbsolutePath(), REMOTE_PATH + "/" + MIRRORTOUCH_BIN);
				//mirrortouchBinFile.delete();
			}
			
			//mDevice.installPackage(mirrorAgentFile.getAbsolutePath(), true);
			AnsiLog.i2(1001, "[" + mDeviceModel + "] " + "The MIRROR-MODULE INSTALLED on android.");
		} catch (IOException e) {
			success = false;
		} catch (AdbCommandRejectedException e) {
			success = false;
		} catch (TimeoutException e) {
			success = false;
		} catch (SyncException e) {
			success = false;
		}
		
		if(!success) {
			AnsiLog.e(9201, "[" + mDeviceModel + "] " + "An error occurred while installing the MIRROR-MODULE on Android.");
			return false;
		}
		
		// write version to android
		executeShellCommand(MIRROR_RM_VER_COMMAND);
		executeShellCommand(String.format(MIRROR_VER_COMMAND, Constant.MIRROR_VER));
		executeShellCommand(String.format(MIRROR_CHMOD_COMMAND, MIRROR_BIN));
		executeShellCommand(String.format(MIRROR_CHMOD_COMMAND, MIRRORTOUCH_BIN));
		
		return installApk();
	}
	
	private boolean installApk() {
		boolean success = true;
		int result = 0;
		Map<String, String> mapRes;
		
		mapRes = installApkPkg(AGENT_MIRROR_APK, AGENT_SERVICE_PKG);
		try {
			result = Integer.valueOf(mapRes.get("result"));
		} catch(NumberFormatException e) {}
		success = result == 0 ? false : true;
		
		if(result == 0) {
			AnsiLog.e(9202, "[" + mDeviceModel + "] " + "An error occurred while installing the MIRROR-AGENT on Android. " + mapRes.get("sError"));
			return false;
		}
		else if(result == 1)
			AnsiLog.i2(1002, "[" + mDeviceModel + "] " + "The AGENT-SERVICE-MODULE(VersionCode: " + mapRes.get("verApk") + ") INSTALLED on android.");
		else
			AnsiLog.i2(1002, "[" + mDeviceModel + "] " + "The AGENT-SERVICE-MODULE(VersionCode: " + mapRes.get("verPkg") + ") has been ALREADY INSTALLED on android.");
		
		if(ServerModule.get().isUseKoreanKbd()) {
			mapRes = installApkPkg(KOR_KBD_APK, KOR_KBD_PKG);
			try {
				result = Integer.valueOf(mapRes.get("result"));
			} catch(NumberFormatException e) {}
			
			if(result == 0)
				AnsiLog.w(9203, "[" + mDeviceModel + "] " + "An error occurred while installing the H/W Korean SoftKeyboard on Android. " + mapRes.get("sError"));
			else if(result == 1)
				AnsiLog.i2(1003, "[" + mDeviceModel + "] " + "The KOR_KBD-MODULE(VersionCode: " + mapRes.get("verApk") + ") INSTALLED on android.");
			else
				AnsiLog.i2(1003, "[" + mDeviceModel + "] " + "The KOR_KBD-MODULE(VersionCode: " + mapRes.get("verPkg") + ") has been ALREADY INSTALLED on android.");
		}
		else {
			mapRes = installApkPkg(ADB_KBD_APK, ADB_KBD_PKG);
			try {
				result = Integer.valueOf(mapRes.get("result"));
			} catch(NumberFormatException e) {}
			
			if(result == 0)
				AnsiLog.w(9203, "[" + mDeviceModel + "] " + "An error occurred while installing the H/W ADB SoftKeyboard on Android. " + mapRes.get("sError"));
			else if(result == 1)
				AnsiLog.i2(1003, "[" + mDeviceModel + "] " + "The ADB_KBD-MODULE(VersionCode: " + mapRes.get("verApk") + ") INSTALLED on android.");
			else
				AnsiLog.i2(1003, "[" + mDeviceModel + "] " + "The ADB_KBD-MODULE(VersionCode: " + mapRes.get("verPkg") + ") has been ALREADY INSTALLED on android.");
		}
		return success;
	}
	
	private Map<String, String> installApkPkg(String apk, String pkg) {
		Map<String, String> mapRes = new HashMap<String, String>();
		String sError = "";
		int result = 0;
		int verPkg = getPackageVersion(pkg);
		int verApk = getApkVersionCode(Constant.ROOT + File.separator + apk);
		
		if(verPkg > 0 && verPkg > verApk) {
			try {
				mDevice.uninstallPackage(pkg);
			} catch (InstallException e) {
				sError = e.toString();
				mapRes.put("verPkg", String.valueOf(verPkg));
				mapRes.put("verApk", String.valueOf(verApk));
				mapRes.put("sError", sError);
				mapRes.put("result", "0");
				return mapRes;
			}
			verPkg = 0;
		}
		
		if(verPkg == 0 || verPkg < verApk) {
			File apkFile = new File(Constant.ROOT, apk);
			if(apkFile == null || !apkFile.exists()) {
				sError = "Not Found APK File(" + apk + ")";
			}
			else {
				int cnt = 0;
				while(cnt < 2) {
					try {
						mDevice.installPackage(apkFile.getAbsolutePath(), true);
						result = 1;
						sError = "";
						cnt = 2;
					} catch (InstallException e) {
						sError = e.toString();
						cnt++;
						if(verPkg > 0 && cnt < 2) {
							try {
								mDevice.uninstallPackage(pkg);
							} catch (InstallException eu) {
								sError = eu.toString();
								break;
							}
						}
					}
				}
			}
		}
		else {
			result = 2;
		}
		
		mapRes.put("verPkg", String.valueOf(verPkg));
		mapRes.put("verApk", String.valueOf(verApk));
		mapRes.put("sError", sError);
		mapRes.put("result", String.valueOf(result));
		return mapRes;
	}
	
	private int getPackageVersion(String pkg) {
		int versionCode = 0;
		String cmd = String.format(GET_PKG_VER_COMMAND, pkg);
		String output = executeShellCommand(cmd).trim();
		
		Pattern p = Pattern.compile("versionCode\\=([0-9]+)");
		Matcher m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 1) { // groupCount bug???(+1)
				try {
					versionCode = Integer.valueOf(m.group(1));
				} catch(NumberFormatException e) {}
			}
		}
		return versionCode;
	}
	
	protected boolean isInstallPackage(String pkg) {
		String output = executeShellCommand("pm path " + pkg).trim();
		return !output.isEmpty();
	}
	
	private int getApkVersionCode(String apk) {
		int versionCode = 0;
		String output;
		String localPath = System.getProperty("user.dir") + File.separator + "platform-tools";
		String aapt = localPath + File.separator + "aapt dump badging ";
		String cmd = aapt + apk;
		
		output = execCmd(cmd).trim();
		if(!output.isEmpty()) {
			Pattern p = Pattern.compile("versionCode\\='([0-9]+)' version");
			Matcher m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					try { 
						versionCode = Integer.valueOf(m.group(1));
					} catch(Exception e) {}
				}
			}
		}
		return versionCode;
	}
		
	private void initPort() {
		initPortMirror();
		initPortTouch();
		//initPortKbd();
		initPortAgent();
		initPortService();
	}
	
	private boolean initPortMirror() {
		boolean success = false;
		try {
			ServerSocket socMirror = new ServerSocket(0);
			MIRROR_PORT = socMirror.getLocalPort();
			socMirror.close();
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9204, "[" + mDeviceModel + "] " + "Failed to MIRROR-PORT(" + MIRROR_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
	private boolean initPortTouch() {
		boolean success = false;
		try {
			ServerSocket socTouch = new ServerSocket(0);
			TOUCH_PORT = socTouch.getLocalPort();
			socTouch.close();
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9205, "[" + mDeviceModel + "] " + "Failed to TOUCH-PORT(" + TOUCH_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
	private boolean initPortService() {
		boolean success = false;
		try {
			ServerSocket socket = new ServerSocket(0);
			SERVICE_PORT = socket.getLocalPort();
			socket.close();
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9206, "[" + mDeviceModel + "] " + "Failed to SERVICE-PORT(" + SERVICE_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
//	private boolean initPortKbd() {
//		boolean success = false;
//		try {
//			ServerSocket socket = new ServerSocket(0);
//			KBD_PORT = socket.getLocalPort();
//			socket.close();
//			success = true;
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		if(!success) {
//			AnsiLog.e(9207, "[" + mDeviceModel + "] " + "Failed to KBD-PORT(" + KBD_PORT + ") of the local agent computer.");
//			return false;
//		}
//		return success;
//	}
	
	private boolean initPortAgent() {
		boolean success = false;
		try {
			ServerSocket socket = new ServerSocket(0);
			AGENT_PORT = socket.getLocalPort();
			socket.close();
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9207, "[" + mDeviceModel + "] " + "Failed to AGENT-PORT(" + AGENT_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
	private boolean initForwardMirror(IDevice dev) {
		boolean success = false;
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward tcp:" + MIRROR_PORT + " MIRROR-MODULE of Android");
			dev.createForward(MIRROR_PORT, "mirror", DeviceUnixSocketNamespace.ABSTRACT);
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9208, "[" + mDeviceModel + "] " + "Failed to FORWARD \"the MIRROR-MODULE of Android\" to PORT(" + MIRROR_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
	private boolean initForwardTouch(IDevice dev) {
		boolean success = false;
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward tcp:" + TOUCH_PORT + " TOUCH-MODULE of Android");
			dev.createForward(TOUCH_PORT, "mirrortouch", DeviceUnixSocketNamespace.ABSTRACT);
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9209, "[" + mDeviceModel + "] " + "Failed to FORWARD \"the TOUCH-MODULE of Android\" to PORT(" + TOUCH_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
	private boolean initForwardService(IDevice dev) {
		boolean success = false;
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward tcp:" + SERVICE_PORT + " SERVICE-MODULE of Android");
			dev.createForward(SERVICE_PORT, "mirrorservice", DeviceUnixSocketNamespace.ABSTRACT);
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9210, "[" + mDeviceModel + "] " + "Failed to FORWARD \"the SERVICE-MODULE of Android\" to PORT(" + SERVICE_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
	private boolean initForwardAgent(IDevice dev) {
		boolean success = false;
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward tcp:" + AGENT_PORT + " AGENT-MODULE of Android");
			dev.createForward(AGENT_PORT, AGENT_SERVICE_SOCKET, DeviceUnixSocketNamespace.ABSTRACT);
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		if(!success) {
			AnsiLog.e(9210, "[" + mDeviceModel + "] " + "Failed to FORWARD \"the AGENT-MODULE of Android\" to PORT(" + AGENT_PORT + ") of the local agent computer.");
			return false;
		}
		return success;
	}
	
//	private boolean initForwardKbd(IDevice dev) {
//		boolean success = false;
//		try {
//			String sockName = KOR_KBD_SOCKET;
//			if(!ServerModule.get().isUseKoreanKbd())
//				sockName = ADB_KBD_SOCKET;
//			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward tcp:" + KBD_PORT + " KBD-MODULE of Android");
//			dev.createForward(KBD_PORT, sockName, DeviceUnixSocketNamespace.ABSTRACT);
//			success = true;
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (AdbCommandRejectedException e) {
//			e.printStackTrace();
//		} catch (TimeoutException e) {
//			e.printStackTrace();
//		}
//		if(!success) {
//			AnsiLog.e(9211, "[" + mDeviceModel + "] " + "Failed to FORWARD \"the KBD-MODULE of Android\" to PORT(" + KBD_PORT + ") of the local agent computer.");
//			return false;
//		}
//		return success;
//	}
		
	public boolean initWindow() {
		Window window = dumpWindow();
		if(window == null)
			return false;
		mWindow = window;
		
		initVirtualSize();
		
		AnsiLog.i3("[" + mDeviceModel + "] " + "Window info: " + window.toString());
		return true;
	}
	
	private void initVirtualSize() {
		int w, h, s;
		w = mWindow.getCurrentWidth();
		h = mWindow.getCurrentHeight();
		s = Math.max(w, h);
		w = s; h = s;
		mPhysicalSize = String.valueOf(w) + "x" + String.valueOf(h);
		w = mWindow.getVirtualWidth();
		h = mWindow.getVirtualHeight();
		s = Math.max(w, h);
		w = s; h = s;
		mVirtualSize = String.valueOf(w) + "x" + String.valueOf(h);
	}
	
	private void initKeyDeviceCode() {
		String output = executeShellCommand(GETEVENT_COMMAND);
		mKeyDevices = new String[]{"","","","","","","",""};
		
		try {
			int done = 0;
			int idx = output.length()-1;
			while(idx > 0) {
				String keyline, devline, devpath = "";
				int fdx = output.lastIndexOf("KEY ", idx);
				if(fdx < 0) break;
				int ldx = output.indexOf('\n', fdx+11);
				if(ldx < fdx+11) break;
				keyline = output.substring(fdx+11, ldx);
				
				fdx = output.lastIndexOf("add device ", fdx);
				if(fdx < 0) break;
				ldx = output.indexOf('\n', fdx+11);
				if(ldx < fdx+11) break;
				devline = output.substring(fdx+11, ldx);
				if(devline.indexOf(':') >= 0)
					devpath = devline.split(":")[1].trim();
				
				for(int i=0;i<mKeyDevices.length;i++) {
					if(mKeyDevices[i].isEmpty()) {
						String s = String.format(" %04x", mKeyCodes[i]);
						if(keyline.indexOf(s) >= 0) {
							mKeyDevices[i] = devpath;
							done++;
							if(done == mKeyDevices.length)
								break;
						}
					}
				}
				if(done == mKeyDevices.length)
					idx = 0;
				else
					idx = fdx;
			}
		} catch(Exception e) {}
		AnsiLog.d("hw key devices : " + String.join(",", mKeyDevices));
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
			AnsiLog.e(9212, "[" + mDeviceModel + "] " + "Failed to get the screen RESOLUTION of Android.");
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
		
		output = executeShellCommand(MIRROR_DUMPSYS_BATTERY_HEALTH_COMMAND);
		p = Pattern.compile("health: ([0-9]+)");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 1) { // groupCount bug???(+1)
				try {
					window.battery = (Integer.valueOf(m.group(1)));
				} catch(NumberFormatException e) {}
			}
		}
		
		output = executeShellCommand(MIRROR_DUMPSYS_WIFI_COMMAND);
		p = Pattern.compile("state: ([A-Z]+)");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 1) { // groupCount bug???(+1)
				try {
					window.isWifi = m.group(1).equals("CONNECTED");
				} catch(NumberFormatException e) {}
			}
			if(window.isWifi) {
				p = Pattern.compile("state: .+extra: \"(.+)\"");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try {
							window.wifiAP = m.group(1);
						} catch(NumberFormatException e) {}
					}
				}
			}
		}
		
		output = executeShellCommand(MIRROR_PROC_MEMTOTAL_COMMAND);
		p = Pattern.compile("MemTotal: +([0-9]+) kB");
		m = p.matcher(output);
		if(m.find()) {
			if(m.groupCount() == 1) { // groupCount bug???(+1)
				try {
					window.memory = (Integer.valueOf(m.group(1)));
				} catch(NumberFormatException e) {}
			}
		}
		
		return window;
	}
	
	public JSONObject getDeviceInfo() {
		try {
			JSONObject jsonItem = new JSONObject();
			
			jsonItem.put("serial", mDevice.getSerialNumber());
			jsonItem.put("name", mDevice.getName());
			jsonItem.put("model", mDevice.getProperty(IDevice.PROP_DEVICE_MODEL));
			jsonItem.put("api", mDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL));
			jsonItem.put("sdk", mDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL));
			jsonItem.put("version", mDevice.getProperty(IDevice.PROP_BUILD_VERSION));
			jsonItem.put("manufacturer", mDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER));
			jsonItem.put("sim", mDevice.getProperty("gsm.sim.operator.numeric"));
			jsonItem.put("memory", mWindow.memory); // KB
			jsonItem.put("battery", mWindow.battery); // battery health
			jsonItem.put("enable_wifi", mWindow.isWifi ? 1 : 0);
			if(mWindow.isWifi) jsonItem.put("ap_wifi", mWindow.wifiAP);
			jsonItem.put("cpu", mDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST));
			if(!jsonItem.has("cpu") || jsonItem.getString("cpu").isEmpty()) {
				jsonItem.put("cpu", mDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI));
				if(!jsonItem.has("cpu") || jsonItem.getString("cpu").isEmpty())
					jsonItem.put("cpu", "armeabi-v7a");
			}
			jsonItem.put("region", mDevice.getProperty(IDevice.PROP_DEVICE_REGION));
			if(!jsonItem.has("region") || jsonItem.getString("region").isEmpty())
				jsonItem.put("region", "kr");
			jsonItem.put("language", mDevice.getProperty(IDevice.PROP_DEVICE_LANGUAGE));
			if(!jsonItem.has("language") || jsonItem.getString("language").isEmpty())
				jsonItem.put("language", "ko");
			
			jsonItem.put("window_info", 1);
			jsonItem.put("physical_width", mWindow.getPhysicalWidth());
			jsonItem.put("physical_height", mWindow.getPhysicalHeight());
			jsonItem.put("current_width", mWindow.getCurrentWidth());
			jsonItem.put("current_height", mWindow.getCurrentHeight());
			jsonItem.put("app_width", mWindow.getAppWidth());
			jsonItem.put("app_height", mWindow.getAppHeight());
			jsonItem.put("virtual_width", mWindow.getVirtualWidth());
			jsonItem.put("virtual_height", mWindow.getVirtualHeight());
			jsonItem.put("enable_rotation", mWindow.getSensor() ? 1 : 0);
			jsonItem.put("current_rotation", mWindow.getRotation());
			jsonItem.put("density", mWindow.getDensity());
			jsonItem.put("dpix", mWindow.getDpiX());
			jsonItem.put("dpiy", mWindow.getDpiY());
			
			return jsonItem;
		} catch(JSONException e) {}
		
		return null;
	}
	
	private void initSaveConfig() {
		JSONObject objRoot = mCfg.getObject();
		
		try {
			JSONObject objWin = mWindow.toJson();
			JSONObject objDev = new JSONObject();
			JSONObject objPort = new JSONObject();
			JSONObject objKey = new JSONObject();
			JSONArray arrKey = new JSONArray();
			
			objPort.put("mirror", MIRROR_PORT);
			objPort.put("touch", TOUCH_PORT);
			objPort.put("service", SERVICE_PORT);
			objPort.put("agent", AGENT_PORT);
			//objPort.put("kbd", KBD_PORT);
			
			for(int i=0;i<mKeyDevices.length;i++)
				arrKey.put(mKeyDevices[i]);
			objKey.put("devs", arrKey);
			
			objDev.put("window", objWin);
			objDev.put("port", objPort);
			objDev.put("key", objKey);
			
			objRoot.put(mDeviceName, objDev);
			
		} catch(JSONException e) {
			
		}
	}
	
	private void uninitSaveConfig() {
		JSONObject objRoot = mCfg.getObject();
		objRoot.remove(mDeviceName);
	}
	
	private void uninitForward() {
		uninitForwardMirror(mDevice);
		uninitForwardTouch(mDevice);
		//uninitForwardKbd(mDevice);
		uninitForwardAgent(mDevice);
		uninitForwardService(mDevice);
	}
	
	private void uninitForwardMirror(IDevice dev) {
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward --remove tcp:" + MIRROR_PORT + " MIRROR-MODULE of Android");
			dev.removeForward(MIRROR_PORT, "mirror", DeviceUnixSocketNamespace.ABSTRACT);
		} catch (IOException e) {
			//e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			//e.printStackTrace();
		} catch (TimeoutException e) {
			//e.printStackTrace();
		}

	}
	
	private void uninitForwardTouch(IDevice dev) {
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward --remove tcp:" + TOUCH_PORT + " TOUCH-MODULE of Android");
			dev.removeForward(TOUCH_PORT, "mirrortouch", DeviceUnixSocketNamespace.ABSTRACT);
		} catch (IOException e) {
			//e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			//e.printStackTrace();
		} catch (TimeoutException e) {
			//e.printStackTrace();
		}
	}
	
	private void uninitForwardService(IDevice dev) {
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward --remove tcp:" + SERVICE_PORT + " SERVICE-MODULE of Android");
			dev.removeForward(SERVICE_PORT, "mirrorservice", DeviceUnixSocketNamespace.ABSTRACT);
		} catch (IOException e) {
			//e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			//e.printStackTrace();
		} catch (TimeoutException e) {
			//e.printStackTrace();
		}
	}
	
	private void uninitForwardAgent(IDevice dev) {
		try {
			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward --remove tcp:" + AGENT_PORT + " AGENT-MODULE of Android");
			dev.removeForward(AGENT_PORT, AGENT_SERVICE_SOCKET, DeviceUnixSocketNamespace.ABSTRACT);
		} catch (IOException e) {
			//e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			//e.printStackTrace();
		} catch (TimeoutException e) {
			//e.printStackTrace();
		}
	}
	
//	private void uninitForwardKbd(IDevice dev) {
//		try {
//			String sockName = KOR_KBD_SOCKET;
//			if(!ServerModule.get().isUseKoreanKbd())
//				sockName = ADB_KBD_SOCKET;
//			AnsiLog.d("[" + mDeviceModel + "] " + "adb forward --remove tcp:" + KBD_PORT + " KBD-MODULE of Android");
//			dev.removeForward(KBD_PORT, sockName, DeviceUnixSocketNamespace.ABSTRACT);
//		} catch (IOException e) {
//			//e.printStackTrace();
//		} catch (AdbCommandRejectedException e) {
//			//e.printStackTrace();
//		} catch (TimeoutException e) {
//			//e.printStackTrace();
//		}
//	}
	
	public void uninit() {
		mWindow = new Window();
		mPhysicalSize = mVirtualSize = "";
		uninitForward();
		uninitSaveConfig();
		System.gc();
	}
	
	public Window getWindow() {
		return mWindow;
	}
	
	public void start() {
		isRunning = true;
		startMirror();
		startTouch();
		startKbd();
		//initForwardService(mDevice);
		startService();
	}
	
	public void waiting() {
		try {
			mEngineThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
    	}
    	catch (InterruptedException e) {}
	}
	
	public void stop() {
		isRunning = false;
		stopMirror();
		stopTouch();
		stopService();
		stopKbd();
		
		try {
			mEngineThreadPool.shutdown();
			mEngineThreadPool.awaitTermination(2000, TimeUnit.MILLISECONDS);
    	}
    	catch (InterruptedException e) {
    	}
    	finally {
    	    if (!mEngineThreadPool.isTerminated()) {
    	    	mEngineThreadPool.shutdownNow();
    	    }
    	}
		
		uninit();
	}

	class RunMirrorByAdbShell implements Runnable {
		private CollectingOutputReceiver adbShellCommand;
		private String cmd;
		private int port;
		
		public RunMirrorByAdbShell(CollectingOutputReceiver shell, String cmd, int port) {
			this.adbShellCommand = shell;
			this.cmd = cmd;
			this.port = port;
		}
		
		public void run() {
			String output = "";
			ServerModule mod = ServerModule.get();
			IDevice dev = mDevice;
			while(mod.isRun() && isRunning) {
				if(dev == null) {
					dev = mod.getDevice(mDeviceSerial);
					if(dev == null) {
						try { Thread.sleep(1000); } catch(InterruptedException e) {}
						continue;
					}
					else {
						mDevice = dev;
					}
				}
				
				if(port == MIRROR_PORT) {
					initForwardMirror(dev);
					AnsiLog.i(1005, "[" + mDeviceModel + "] " + "Run the MIRROR-MODULE of Android.");
				}
				else if(port == TOUCH_PORT) {
					initForwardTouch(dev);
					//initForwardKbd(dev);
					AnsiLog.i(1006, "[" + mDeviceModel + "] " + "Run the TOUCH-MODULE of Android.");
				}
				else if(port == SERVICE_PORT) {
					initForwardService(dev);
					AnsiLog.i(1007, "[" + mDeviceModel + "] " + "Run the SERVICE-MODULE of Android.");
					//return;
				}
				else if(port == AGENT_PORT) {
					initForwardAgent(dev);
					AnsiLog.i(1008, "[" + mDeviceModel + "] " + "Run the AGENT-MODULE of Android.");
				}
				AnsiLog.d("[" + mDeviceModel + "] " + "cmd:" + cmd);
				output = executeShellCommand(dev, cmd, adbShellCommand);
				//AnsiLog.d("[" + mDeviceModel + "] " + "exit cmd:" + cmd);
				//if(port == SERVICE_PORT)
					//AnsiLog.d("[" + mDeviceModel + "] " + "exit output:" + output);
				
				try { Thread.sleep(500); } catch(InterruptedException e) {}
				dev = null;
			}
		}
	}
	
	private void startMirror() {
		String cmd = String.format(MIRROR_START_COMMAND, ServerModule.get().getImageQuality(), mPhysicalSize, mVirtualSize, 0);//mWindow.getOrientation());
		mMirrorCmdAdb = new CollectingOutputReceiver();
		mEngineThreadPool.submit(new RunMirrorByAdbShell(mMirrorCmdAdb, cmd, MIRROR_PORT));
		
		try { Thread.sleep(500); } catch(InterruptedException e) {}
	}
	
	private void stopMirror() {
		
		if(mMirrorCmdAdb != null) {
			if(!mMirrorCmdAdb.isCancelled())
				mMirrorCmdAdb.cancel();
			mMirrorCmdAdb = null;
		
			try {
				Thread.sleep(100);

				String output = executeShellCommand(MIRROR_PS_MIRROR_COMMAND);
				if(output.length() > 0) {
					String w = "", binMirrorTouch = REMOTE_PATH + "/" + MIRRORTOUCH_BIN, binMirror = REMOTE_PATH + "/" + MIRROR_BIN;
					int mirrorPid = 0, mirrorTouchPid = 0, pid = 0, idx = -1;
					for(int i=0;i<output.length();i++) {
						char c = output.charAt(i);
						
						if(c == ' ' || c == '\t') {
							if(w.length() > 0) {
								idx++;
								if(idx == 1) {
									try {
										pid = Integer.valueOf(w);
									} catch(NumberFormatException e) {
										pid = 0;
									}
								}
								w = "";
							}
						}
						else if(c == '\r') {}
						else if(c == '\n') {
							if(pid > 0) {
								if(w.compareTo(binMirrorTouch) == 0)
									mirrorTouchPid = pid;
								else if(w.compareTo(binMirror) == 0)
									mirrorPid = pid;
								pid = 0;
							}
							idx = -1;
						}
						else if(i == output.length()-1) {
							w += c;
							if(pid > 0) {
								if(w.compareTo(binMirrorTouch) == 0)
									mirrorTouchPid = pid;
								else if(w.compareTo(binMirror) == 0)
									mirrorPid = pid;
								pid = 0;
							}
						}
						else {
							w += c;
						}
					}
					
					if(mirrorPid > 0) {
						String cmd = MIRROR_KILL_COMMAND + mirrorPid;
						executeShellCommand(cmd);
					}
				}
			} catch(Exception e) {
				
			}
		}
	}

	private int isOnlineInLocalAdb(int port) {
	    int b = 1;
        InetSocketAddress sa = new InetSocketAddress("localhost", port);
	    for(int i=0;i<5;i++) {
		    try{
		        Socket ss = new Socket();
		        ss.connect(sa, 1);
		        ss.close();
		        b = 1;
		        return b;
		    }
		    catch(Exception e) {
		        b = 0;
		        try { Thread.sleep(1000); }
		        catch(InterruptedException es) { return -1; }
		    }
	    }
	    return b;
	}


	
	private void startTouch() {
		mTouchCmdAdb = new CollectingOutputReceiver();
		mEngineThreadPool.submit(new RunMirrorByAdbShell(mTouchCmdAdb, MIRRORTOUCH_START_COMMAND, TOUCH_PORT));

		try { Thread.sleep(500); } catch(InterruptedException e) {}
	}
	
	private void stopTouch() {
		if(mTouchCmdAdb != null) {
			if(!mTouchCmdAdb.isCancelled())
				mTouchCmdAdb.cancel();
			mTouchCmdAdb = null;
			
			try {
				Thread.sleep(100);

				String output = executeShellCommand(MIRROR_PS_MIRRORTOUCH_COMMAND);
				if(output.length() > 0) {
					String w = "", binMirrorTouch = REMOTE_PATH + "/" + MIRRORTOUCH_BIN;
					int mirrorTouchPid = 0, pid = 0, idx = -1;
					for(int i=0;i<output.length();i++) {
						char c = output.charAt(i);
						
						if(c == ' ' || c == '\t') {
							if(w.length() > 0) {
								idx++;
								if(idx == 1) {
									try {
										pid = Integer.valueOf(w);
									} catch(NumberFormatException e) {
										pid = 0;
									}
								}
								w = "";
							}
						}
						else if(c == '\r') {}
						else if(c == '\n') {
							if(pid > 0) {
								if(w.compareTo(binMirrorTouch) == 0)
									mirrorTouchPid = pid;
								pid = 0;
							}
							idx = -1;
						}
						else if(i == output.length()-1) {
							w += c;
							if(pid > 0) {
								if(w.compareTo(binMirrorTouch) == 0)
									mirrorTouchPid = pid;
								pid = 0;
							}
						}
						else {
							w += c;
						}
					}
					
					if(mirrorTouchPid > 0) {
						String cmd = MIRROR_KILL_COMMAND + mirrorTouchPid;
						executeShellCommand(cmd);
					}
				}
			} catch(Exception e) {
				
			}
		}
	}

	private void startService() {
		mServiceCmdAdb = new CollectingOutputReceiver();
		mEngineThreadPool.submit(new RunMirrorByAdbShell(mServiceCmdAdb, AGENT_SERVICE_START_COMMAND, SERVICE_PORT));

		try { Thread.sleep(500); } catch(InterruptedException e) {}
	}

	private void stopService() {
		if(mServiceCmdAdb != null) {
			if(!mServiceCmdAdb.isCancelled())
				mServiceCmdAdb.cancel();
			mServiceCmdAdb = null;
			
			try {
				Thread.sleep(100);
				executeShellCommand(AGENT_SERVICE_STOP_COMMAND);
			} catch(Exception e) {				
			}
		}
		
	}

	private void startKbd() {
		mDefaultKbd = executeShellCommand(AGENT_GET_KBD_COMMAND).trim();
		if(ServerModule.get().isUseKoreanKbd()) {
			if(mDefaultKbd.compareTo(KOR_KBD_IME) != 0) {
				executeShellCommand(KOR_KBD_SET_COMMAND);
			}
		}
		else {
			if(mDefaultKbd.compareTo(ADB_KBD_IME) != 0) {
				executeShellCommand(ADB_KBD_SET_COMMAND);
			}
		}
		
		String apkPath = executeShellCommand(AGENT_GET_PATH_COMMAND).trim();
		if(apkPath != null && !apkPath.isEmpty()) {
			apkPath = apkPath.replaceAll("package:", "");
			
			String cmd = String.format(AGENT_START_COMMAND, apkPath);
			mAgentCmdAdb = new CollectingOutputReceiver();
			mEngineThreadPool.submit(new RunMirrorByAdbShell(mAgentCmdAdb, cmd, AGENT_PORT));
			
			try { Thread.sleep(500); } catch(InterruptedException e) {}
		}
	}

	private void stopKbd() {
		if(mAgentCmdAdb != null) {
			if(!mAgentCmdAdb.isCancelled())
				mAgentCmdAdb.cancel();
			mAgentCmdAdb = null;
			
			try {
				Thread.sleep(100);

				String output = executeShellCommand(AGENT_CONSOLE_PS_APP_COMMAND);
				if(output.length() > 0) {
					String w = "", binAgent = AGENT_CONSOLE_NAME;
					int agentPid = 0, pid = 0, idx = -1;
					for(int i=0;i<output.length();i++) {
						char c = output.charAt(i);
						
						if(c == ' ' || c == '\t') {
							if(w.length() > 0) {
								idx++;
								if(idx == 1) {
									try {
										pid = Integer.valueOf(w);
									} catch(NumberFormatException e) {
										pid = 0;
									}
								}
								w = "";
							}
						}
						else if(c == '\r') {}
						else if(c == '\n') {
							if(pid > 0) {
								if(w.compareTo(binAgent) == 0)
									agentPid = pid;
								pid = 0;
							}
							idx = -1;
						}
						else if(i == output.length()-1) {
							w += c;
							if(pid > 0) {
								if(w.compareTo(binAgent) == 0)
									agentPid = pid;
								pid = 0;
							}
						}
						else {
							w += c;
						}
					}
					
					if(agentPid > 0) {
						String cmd = MIRROR_KILL_COMMAND + agentPid;
						executeShellCommand(cmd);
					}
				}
			} catch(Exception e) {
				
			}
		}
		
		
		if(mDefaultKbd != null) {
			if(ServerModule.get().isUseKoreanKbd()) {
				if(mDefaultKbd.compareTo(KOR_KBD_IME) != 0) {
					String cmd = "ime set " + mDefaultKbd;
					executeShellCommand(cmd);
				}
			}
			else {
				if(mDefaultKbd.compareTo(ADB_KBD_IME) != 0) {
					String cmd = "ime set " + mDefaultKbd;
					executeShellCommand(cmd);
				}
			}
		}
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

	private String executeShellCommand(String command, CollectingOutputReceiver outputReceiver) {
		return executeShellCommand(mDevice, command, outputReceiver);
	}
		
	private String executeShellCommand(String command) {
		return executeShellCommand(mDevice, command);
	}
	
	private static String execCmd(String cmd) {
	    String val = "";
		try {
		    Process proc = Runtime.getRuntime().exec(cmd);
		    java.io.InputStream is = proc.getInputStream();
		    java.io.BufferedReader stdis = new java.io.BufferedReader(new java.io.InputStreamReader(is));
		    StringBuffer stdout = new StringBuffer();
		    String s = null;
		    while ((s = stdis.readLine()) != null && !s.isEmpty()) {
		        stdout.append(s);
		        stdout.append("\n");
		    }
		    val = stdout.toString();
		}
		catch(java.io.IOException e) {e.printStackTrace();}
	    return val;
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
