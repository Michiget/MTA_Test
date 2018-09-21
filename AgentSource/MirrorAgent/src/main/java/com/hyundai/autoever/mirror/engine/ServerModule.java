package com.hyundai.autoever.mirror.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.ddmlib.IDevice;
import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.utils.AnsiLog;
import com.hyundai.autoever.utils.ConfigFile;

public class ServerModule {
	public static String APP = "MirrorAgent";
	public static String TITLE = APP + " - HYUNDAI AUTOEVER";
	public static String VERSION = TITLE + " - Version: 1.16";

	private static ServerModule mModule = null;

	private ADB mAdb = null;
	private ConcurrentMap<String, MirrorEngine> mEngines = null;
	private Object mLock = null;
	
	private JSONObject mJsonConfig = null;
	private final static boolean mUseOpengl = false;
	private final static boolean mUseKoreanKbd = true;
	private final static String mUrlSetDevice = "http://116.193.89.47/amt/set_devices.php";
	private final static boolean mUseSetDevice = false;
	private final static String mUrlSetResult = null;
	private final static boolean mUseSetResult = false;
	private final static String mUrlGetApp = null;
	private final static String mUrlSetLog = null;
	private final static boolean mIncludeCpuMemSetResult = true;
	private final static String mAgentName = "local_agent1";
	private final static String mAgentIP = null;

	public static ServerModule get() {
		if(mModule == null)
			mModule = new ServerModule();
		return mModule;
	}

	public ServerModule() {
		mAdb = new ADB();
		mEngines = new ConcurrentHashMap<String, MirrorEngine>();
		mLock = new Object();
	}
	
	private void printVersion() {
		String sTitle = "+-+-+-+-+-+-+-+-+-+ [" + VERSION + "] +-+-+-+-+-+-+-+-+-+";
		int mw = sTitle.length();
		AnsiLog.br(mw);
		AnsiLog.r(sTitle, mw);
		AnsiLog.er(mw);
	}
	
	public void init() {
		removeDownPath();
		AnsiLog.install();

		printVersion();
	}
	
	public void uninit() {
		removeDownPath();
		AnsiLog.uninstall();
	}
	
	private void removeDownPath() {
		String localPath = System.getProperty("user.dir") + File.separator + "down"; 
		File dir = new File(localPath);
		if(dir.exists()) {
			for (File file: dir.listFiles()) {
		        if (!file.isDirectory()) file.delete();
		    }
		}
	}
	
	public boolean setConfigJson(String jsonFile) {
		File file = new File(jsonFile);
		if(file.exists()) {
			try {
				FileInputStream in = new FileInputStream(file);
				byte[] inputBytes = new byte[(int)file.length()];
	            in.read(inputBytes);
	            in.close();
            	String sJson = new String(inputBytes, "UTF-8");
	            try {
		            mJsonConfig = new JSONObject(sJson);
		        } catch(JSONException e) {}
			} catch(IOException e) {}
			
			return true;
		}
		return false;
	}
	
	public boolean isUseOpengl() {
		if(mJsonConfig == null || !mJsonConfig.has("use_opengl"))
			return mUseOpengl;
		try {
			return mJsonConfig.getBoolean("use_opengl");
		} catch(JSONException e) {}
		return mUseOpengl;
	}
	
	public boolean isUseKoreanKbd() {
		if(mJsonConfig == null || !mJsonConfig.has("use_korean_kbd"))
			return mUseKoreanKbd;
		try {
			return mJsonConfig.getBoolean("use_korean_kbd");
		} catch(JSONException e) {}
		return mUseKoreanKbd;
	}
	
	public boolean isUseSetDevice() {
		if(mJsonConfig == null || !mJsonConfig.has("use_set_device"))
			return mUseSetDevice;
		try {
			return mJsonConfig.getBoolean("use_set_device");
		} catch(JSONException e) {}
		return mUseSetDevice;
	}
	
	public String getUrlSetDevice() {
		if(mJsonConfig == null || !mJsonConfig.has("url_set_device"))
			return mUrlSetDevice;
		try {
			return mJsonConfig.getString("url_set_device");
		} catch(JSONException e) {}
		return mUrlSetDevice;
	}
	
	public boolean isIncludeCpuMemSetResult() {
		if(mJsonConfig == null || !mJsonConfig.has("include_cpu_mem_set_result"))
			return mIncludeCpuMemSetResult;
		try {
			return mJsonConfig.getBoolean("include_cpu_mem_set_result");
		} catch(JSONException e) {}
		return mIncludeCpuMemSetResult;
	}
	
	public boolean isUseSetResult() {
		if(mJsonConfig == null || !mJsonConfig.has("use_set_result"))
			return mUseSetResult;
		try {
			return mJsonConfig.getBoolean("use_set_result");
		} catch(JSONException e) {}
		return mUseSetResult;
	}
	
	public String getUrlSetResult() {
		if(mJsonConfig == null || !mJsonConfig.has("url_set_result"))
			return mUrlSetResult;
		try {
			return mJsonConfig.getString("url_set_result");
		} catch(JSONException e) {}
		return mUrlSetResult;
	}
	
	public String getUrlGetApp() {
		if(mJsonConfig == null || !mJsonConfig.has("url_get_app"))
			return mUrlGetApp;
		try {
			return mJsonConfig.getString("url_get_app");
		} catch(JSONException e) {}
		return mUrlGetApp;
	}
	
	public String getUrlSetLog() {
		if(mJsonConfig == null || !mJsonConfig.has("url_set_log"))
			return mUrlSetLog;
		try {
			return mJsonConfig.getString("url_set_log");
		} catch(JSONException e) {}
		return mUrlSetLog;
	}
	
	public String getAgentName() {
		if(mJsonConfig == null || !mJsonConfig.has("agent_name"))
			return mAgentName;
		try {
			return mJsonConfig.getString("agent_name");
		} catch(JSONException e) {}
		return mAgentName;
	}
	
	public String getAgentIP() {
		if(mJsonConfig == null || !mJsonConfig.has("agent_ip"))
			return mAgentIP;
		try {
			return mJsonConfig.getString("agent_ip");
		} catch(JSONException e) {}
		return mAgentIP;
	}
	
	public ADB getADB() {
		synchronized(mLock) {
			return mAdb;
		}
	}
	
	public void loadDevices() {
		ADB adb = getADB();
		if(!adb.initDevices())
			return;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return;
		
        JSONObject objRoot = ConfigFile.get().getObject();
		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getProperty(IDevice.PROP_DEVICE_MODEL) != null) {
				if(objRoot.has(dev.getName())) {
					MirrorEngine engine = new MirrorEngine(dev);
					if(engine.init())
						putEngine(engine.getDeviceSerial(), engine);
				}
			}
		}
		
		printVersion();
	}
	
	public IDevice getDevice(String serial) {
		ADB adb = getADB();
		if(!adb.initDevices())
			return null;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return null;
		
		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getSerialNumber().equalsIgnoreCase(serial))
				return dev;
		}
		return null;
	}
	
	public boolean isConnectedDevice(String serial) {
		return getDevice(serial) != null;
	}
	
	public synchronized MirrorEngine reloadDevice(String serial) {
		MirrorEngine engine = null;
		IDevice dev = getDevice(serial);
		if(dev == null)
			return null;
		
		ConfigFile.get().load();
        JSONObject objRoot = ConfigFile.get().getObject();
		
		if(objRoot.has(dev.getName())) {
			engine = new MirrorEngine(dev);
			if(engine.init())
				putEngine(engine.getDeviceSerial(), engine);
		}
		return engine;
	}
	
	public synchronized ConcurrentMap<String, MirrorEngine> getEngines() {
		synchronized(mLock) {
			return mEngines;
		}
	}
	
	public synchronized MirrorEngine findEngine(String serial) {
		if(serial == null)
			return null;
		
		serial = serial.toLowerCase();
		ConcurrentMap<String, MirrorEngine> engines = getEngines();
		if(engines.containsKey(serial))
			return engines.get(serial);
		
		return reloadDevice(serial);
	}
	
	public synchronized void putEngine(String serial, MirrorEngine engine) {
		serial = serial.toLowerCase();
		getEngines().put(serial, engine);
	}
	
	public synchronized void removeEngine(String serial) {
		if(serial == null)
			return;
		
		serial = serial.toLowerCase();
		ConcurrentMap<String, MirrorEngine> engines = getEngines();
		if(engines.containsKey(serial))
			engines.remove(serial);
	}
	
	public static String getLocalIP(boolean site) {
		String ip = "127.0.0.1";
		try {
			InetAddress inet = getLocalHostLANAddress(site);
			ip = inet.getHostAddress();
		} catch(UnknownHostException e) {
			e.printStackTrace();
		}
		return ip;
	}
	
	private static InetAddress getLocalHostLANAddress(boolean site) throws UnknownHostException {
	    try {
	    	InetAddress publicAddress = null;
	        InetAddress stieAddress = null;
	        // Iterate all NICs (network interface cards)...
	        for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
	            NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
	            // Iterate all IP addresses assigned to each card...
	            for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) {
	                InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
	                
//	                System.out.println("ip : " + inetAddr.getHostAddress()
//	                + ", loop : " + inetAddr.isLoopbackAddress()
//	                + ", link : " + inetAddr.isLinkLocalAddress()
//	                + ", any  : " + inetAddr.isAnyLocalAddress()
//	                + ", site : " + inetAddr.isSiteLocalAddress()
//	                );
	                
	                if (!inetAddr.isLoopbackAddress() && inetAddr instanceof Inet4Address) {
	                	if (inetAddr.isSiteLocalAddress()) {
	                    	byte[] ips = inetAddr.getAddress();
	                    	if(ips[0] == (byte)192 && ips[1] == (byte)168 && ips[3] > 2)
	                    		stieAddress = inetAddr;
	                    	else
	                    		stieAddress = inetAddr;
	                    }
	                    else {
	                    	publicAddress = inetAddr;
	                    }
	                }
	            }
	        }
	        if(site && stieAddress != null)
	        	return stieAddress;
	        else if (publicAddress != null)
	            return publicAddress;
	        // At this point, we did not find a non-loopback address.
	        // Fall back to returning whatever InetAddress.getLocalHost() returns...
	        InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
	        if (jdkSuppliedAddress == null) {
	            throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
	        }
	        return jdkSuppliedAddress;
	    }
	    catch (Exception e) {
	        UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
	        unknownHostException.initCause(e);
	        throw unknownHostException;
	    }
	}
}
