package com.hyundai.autoever.mirror.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.ddmlib.IDevice;
import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.utils.AnsiLog;
import com.hyundai.autoever.utils.ConfigFile;

public class ServerModule {
	public static String APP = "DeviceAgent";
	public static String TITLE = APP + " - HYUNDAI AUTOEVER";
	public static String VERSION = TITLE + " - Version: 1.16";

	private static ServerModule mModule = null;
	
	private ADB mAdb = null;
	private ConcurrentMap<String, MirrorEngine> mEngines = null;
	private ExecutorService mExecutorDevice = null;
	private Object mLock = null;
	
	private volatile boolean isRun = false;
	private JSONObject mJsonConfig = null;
	private final static int mImageQuality = 90;
	private final static boolean mUseKoreanKbd = true;
	private final static boolean mUseSetDevice = false;
	private final static String mUrlSetDevice = "http://116.193.89.47/amt/set_devices.php";
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
		AnsiLog.install();

		printVersion();
	}
	
	public void uninit() {
		AnsiLog.uninstall();
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
	
	public int getImageQuality() {
		if(mJsonConfig == null || !mJsonConfig.has("image_quality"))
			return mImageQuality;
		try {
			return mJsonConfig.getInt("image_quality");
		} catch(JSONException e) {}
		return mImageQuality;
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
	
	public IDevice getDevice(String serial) {
		ADB adb = getADB();

		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getSerialNumber().compareToIgnoreCase(serial) == 0) {
				return dev;
			}
		}
		return null;
	}
	
	public boolean initDevices() {
		ADB adb = getADB();
		if(!adb.initDevices())
			return false;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return false;
        
        return true;
	}
	
	private JSONObject jsonRoot = null;
	private JSONArray jsonDevices = null;
	public void loadDevices() {
		ADB adb = getADB();
		
		ConfigFile.get().reset();
		isRun = true;
		
		int cnt = 0;
		String serials = "";
		initSetDevice();
        
		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getProperty(IDevice.PROP_DEVICE_MODEL) != null) {
				MirrorEngine engine = createEngine(dev, true);
				if(engine != null) {
					if(!serials.isEmpty())
						serials += ", ";
					serials += engine.getDeviceModel() + ":" + engine.getDeviceSerial();
					cnt++;
				}
			}
		}
		ConfigFile.get().save();
		
		updateDevice();
		runDevice();
	}
	
	private MirrorEngine createEngine(IDevice dev, boolean put) {
		MirrorEngine engine =  new MirrorEngine(dev);
		if(engine.init()) {
			putEngine(engine.getDeviceSerial(), engine);
			if(put) putSetDevice(engine);
			engine.start();
			return engine;
		}
		return null;
	}
		
	private void initSetDevice() {
		if(isUseSetDevice()) {
			jsonRoot = new JSONObject();
			jsonDevices = new JSONArray();

			String agent, agent_ip;
			agent = getAgentName();
			agent_ip = getAgentIP();
			try {
				jsonRoot.put("agent", agent);
		        if(agent_ip == null)
		        	jsonRoot.put("agent_ip", getLocalIP(agent.contains("local")));
		        else
		        	jsonRoot.put("agent_ip", agent_ip);
			} catch(JSONException e) {}
		}
	}
	
	private void putSetDevice(MirrorEngine engine) {
		if(isUseSetDevice()) {
			JSONObject jsonDev = engine.getDeviceInfo();
	        jsonDevices.put(jsonDev);
		}
	}
	
	private void updateDevice() {
		if(isUseSetDevice()) {
			try {
				jsonRoot.put("items", jsonDevices);
				
				String sTitle = "The Connected DEVICES is : ", sCnt, sDev;
				int mw = sTitle.length();
				List<String> list = new ArrayList<String>();
				int cnt = jsonDevices.length();
				sCnt = "The Count is [" + cnt + "].";
				if(mw < sCnt.length()) mw = sCnt.length();
				for(int i=0;i<cnt;i++) {
					JSONObject jsonDev = jsonDevices.getJSONObject(i);
					sDev = jsonDev.getString("model") + " : " + jsonDev.getString("serial");
					list.add(sDev);
					if(mw < sDev.length()) mw = sDev.length();
				}
				
				AnsiLog.bb(mw);
				AnsiLog.b(sTitle, mw);
				for(int i=0;i<cnt;i++) {
					sDev = list.get(i);
					AnsiLog.b(sDev, mw);
				}
				AnsiLog.b(sCnt, mw);
				AnsiLog.eb(mw);
			} catch(JSONException e) {}
			
			printVersion();
			
			HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead 
			try {
				String url = ServerModule.get().getUrlSetDevice();
			    HttpPost request = new HttpPost(url);
			    String json = jsonRoot.toString() + "\n";
			    StringEntity params = new StringEntity(json);
			    request.addHeader("content-type", "application/json");
			    request.setEntity(params);
			    HttpResponse httpResponse = httpClient.execute(request);
			    HttpEntity httpEntity = httpResponse.getEntity();
				String sText = EntityUtils.toString(httpEntity, "UTF-8");

			    //System.out.println("response : " + sText);

			} catch (Exception ex) {
			} finally {
			}				
		}
	}
	
	private void runDevice() {
		mExecutorDevice = Executors.newSingleThreadExecutor();
		mExecutorDevice.submit(new Runnable() {
			@Override
			public void run() {
				
				while(isRun) {
					initDevices();
					initSetDevice();
					
					int newCnt = 0, delCnt = 0;
					String newSerials = "", delSerials = "";
					ADB adb = getADB();
					int len = adb.getDevices().length;
					Map<String, IDevice> devices = new HashMap<String, IDevice>();
					for(int i=0;i<len;i++) {
						IDevice dev = adb.getDevices()[i];
						if(dev.getProperty(IDevice.PROP_DEVICE_MODEL) != null) {
							MirrorEngine engine = findEngine(dev.getSerialNumber());
							String serial = dev.getSerialNumber().toLowerCase();
							devices.put(serial, dev);
							if(engine == null) {
								engine = createEngine(dev, false);
								if(engine != null) {
									if(!newSerials.isEmpty())
										newSerials += ", ";
									newSerials += engine.getDeviceModel() + ":" + engine.getDeviceSerial();
									newCnt++;
								}
							}
						}
					}
					
					ConcurrentMap<String, MirrorEngine> engines = getEngines();
					Iterator<String> it = engines.keySet().iterator();
					while(it.hasNext()) {
						String serial = it.next();
						MirrorEngine engine = engines.get(serial);
						if(engine != null) {
							if(!devices.containsKey(serial)) {
								engine.stop();
								if(!delSerials.isEmpty())
									delSerials += ", ";
								delSerials += engine.getDeviceModel() + ":" + engine.getDeviceSerial();
								delCnt++;
								it.remove();
							}
							else {
								putSetDevice(engine);
							}
						}
					}
					
					
					if(newCnt > 0)
						AnsiLog.i5(1010, "NEW [" + newSerials + "] " + "DEVICES(" + newCnt + ").");
					if(delCnt > 0)
						AnsiLog.i6(1011, "REMOVE [" + delSerials + "] " + "DEVICES(" + delCnt + ").");
					
					if(newCnt > 0 || delCnt > 0) {
						ConfigFile.get().save();
						updateDevice();
					}
					
					try {
						Thread.sleep(300);
					} catch(Exception e) {}
				}
			}
		});
	}
	
	private void resetDevices() {
		AnsiLog.install();
		initSetDevice();
		updateDevice();
		ConfigFile.get().delete();
		AnsiLog.uninstall();
	}
	
	public void closeDevices() {
		isRun = false;
		try { Thread.sleep(1); } catch(Exception e) {}
		
		try {
			mExecutorDevice.shutdown();
			mExecutorDevice.awaitTermination(2000, TimeUnit.MILLISECONDS);
    	}
    	catch (InterruptedException e) {
    	}
    	finally {
    	    if (!mExecutorDevice.isTerminated()) {
    	    	mExecutorDevice.shutdownNow();
    	    }
    	}

		ConcurrentMap<String, MirrorEngine> engines = getEngines();
		for(String serial: engines.keySet()) {
			MirrorEngine engine = engines.get(serial);
			engine.stop();
		}
		resetDevices();
		//try { Thread.sleep(1000); } catch(Exception e) {}
	}
	
	public void waitDevices() {
		ConcurrentMap<String, MirrorEngine> engines = getEngines();
		for(String serial: engines.keySet()) {
			MirrorEngine engine = engines.get(serial);
			engine.waiting();
		}
		try {
			mExecutorDevice.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
    	}
    	catch (InterruptedException e) {}
	}
	
	public boolean isRun() {
		return isRun;
	}
	
	public boolean isConnectedDevice(String serial) {
		ADB adb = getADB();
		if(!adb.initDevices())
			return false;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return false;
		
		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getSerialNumber().equalsIgnoreCase(serial) && dev.getProperty(IDevice.PROP_DEVICE_MODEL) != null)
				return true;
		}
		return false;
	}
	
	public void removeMirrorOfAndroid() {
		MirrorEngine.removeMirrorOfAndroid(getADB());
	}
	
	public ConcurrentMap<String, MirrorEngine> getEngines() {
		synchronized(mLock) {
			return mEngines;
		}
	}
	
	public MirrorEngine findEngine(String serial) {
		if(serial == null)
			return null;
		
		serial = serial.toLowerCase();
		ConcurrentMap<String, MirrorEngine> engines = getEngines();
		if(engines.containsKey(serial))
			return engines.get(serial);
		return null;
	}
	
	public void putEngine(String serial, MirrorEngine engine) {
		serial = serial.toLowerCase();
		getEngines().put(serial, engine);
	}
	
	public void removeEngine(String serial) {
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
