package com.hyundai.autoever.mirror.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.SoftReference;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.IDevice.DeviceUnixSocketNamespace;
import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.utils.AnsiLog;

public class ClientService {
	private ADB mAdb = null;
	private IDevice mDev = null;
    private ExecutorService mExecutorService = null;
    private int mServicePort = 0;
	private Queue<SoftReference<JSONObject>> mServiceCmdQueue = new LinkedBlockingQueue<SoftReference<JSONObject>>();
	private volatile boolean isRunning = false;
	
	public ClientService(int servicePort) {
		mServicePort = servicePort;
		isRunning = true;
		
		mAdb = new ADB();
		mExecutorService = Executors.newFixedThreadPool(1);
	}
	
	public boolean start(boolean wait) {
        boolean success = false;
        
		AnsiLog.install();
		while(true) {
			if(!mAdb.initDevices()) {
				AnsiLog.d("failed initDevices adb.");
				break;
			}
	        
	        if(mAdb.getDevices() == null || mAdb.getDevices().length == 0) {
				AnsiLog.d("No connection any device of adb.");
				break;
			}
	        
	        mDev = null;
	        for(int i=0;i<mAdb.getDevices().length;i++) {
		        IDevice dev = mAdb.getDevices()[i];
		        if(dev.getProperty(IDevice.PROP_DEVICE_MODEL) != null) {
					mDev = dev;
					break;
		        }
	        }
	        if(mDev == null) {
	        	AnsiLog.d("Invalid devices of adb.");
				break;
	        }
	        
			try {
				AnsiLog.d("adb forward tcp:" + mServicePort);
				mDev.createForward(mServicePort, "mirrorservice", DeviceUnixSocketNamespace.ABSTRACT);
				success = true;
			} catch (IOException e) {
				AnsiLog.e(e.toString());
			} catch (AdbCommandRejectedException e) {
				AnsiLog.e(e.toString());
			} catch (TimeoutException e) {
				AnsiLog.e(e.toString());
			}
			break;
		}
		
		if(!success) {
			AnsiLog.uninstall();
			return false;
		}
		
		mExecutorService.submit(new ClientMonitor());
		
		if(wait) {
			while(isRunning) {
				try {
					Thread.sleep(2000);
				} catch(Exception e) {}
			}
		}
		return true;
	}
	
	public void stop() {
		isRunning = false;
		try { Thread.sleep(1); } catch (InterruptedException e) {}
		
		try {
			mExecutorService.shutdown();
    	    mExecutorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
    	}
    	catch (InterruptedException e) {
    	}
    	finally {
    	    if (!mExecutorService.isTerminated()) {
    	    	mExecutorService.shutdownNow();
    	    }
    	}
		AnsiLog.uninstall();
	}

	class ClientMonitor implements Runnable {
		private final int MAX_RETRY_CONNECT = 5;
		private int retryConn = 0;
		private boolean isRun = true;
		
		public ClientMonitor() {
			
		}
		
		public boolean isRunning() {
			return isRunning && isRun && !Thread.currentThread().isInterrupted();
		}
		
		@Override
		public void run() {
			while(isRunning()) {
				Socket socket = null;
				BufferedWriter streamWriter = null;
				BufferedReader streamReader = null;
				try {
					socket = new Socket("localhost", mServicePort);
					
					streamWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
					streamReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
					while (isRunning() && socket.isConnected()) {
						String inputStr = null;
						if(streamReader.ready()) {
							inputStr = streamReader.readLine();
					        if(inputStr != null && inputStr.length() > 0) {
					        	JSONObject envelope = new JSONObject(inputStr);
					        	String type = envelope.getString("type");
					        	if(type.compareToIgnoreCase("RotationMonitor") == 0) {
					        		if(envelope.has("rotation")) {
					        			AnsiLog.i2(String.format("rotation: %d", envelope.getInt("rotation")));
					        		}
					        	}
					        }
						}
						if(!mServiceCmdQueue.isEmpty()) {
							SoftReference<JSONObject> obj = mServiceCmdQueue.poll();
							String sCmd = obj.get().toString();
							AnsiLog.d("Service cmd : " + sCmd);
							streamWriter.write(sCmd);
							streamWriter.flush();
							obj.clear();
						}
				        if(mServiceCmdQueue.isEmpty() && inputStr == null) {
				        	if(isRunning() && socket.isConnected())
				        		Thread.sleep(100);
				        }
					}
				} catch(Exception e) {
					if(isRunning()) {
						retryConn++;
						if(retryConn >= MAX_RETRY_CONNECT) {
							isRun = false;
						}
					}
				} finally {
					if (streamWriter != null) {
						try {
							streamWriter.close();
							streamWriter = null;
						} catch (IOException e) {}
					}
					if (streamReader != null) {
						try {
							streamReader.close();
							streamReader = null;
						} catch (IOException e) {}
					}
					if (socket != null && socket.isConnected()) {
						try {
							socket.close();
						} catch (IOException e) {}
					}
				}
				
				if(isRunning())
					try { Thread.sleep(1000); } catch (InterruptedException e) {}
			}
		}
	}

	public void addService(JSONObject json) {
		mServiceCmdQueue.add(new SoftReference<JSONObject>(json));
	}
	
	public void addService(SoftReference<JSONObject> json) {
		mServiceCmdQueue.add(json);
	}

	public void find(String strategy, String selector, boolean multiple) {
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("strategy", strategy);
			json.get().put("selector", selector);
			json.get().put("multiple", multiple);
			
			addService(json);
		} catch(Exception e) {}
	}
	
	public void findCoordinates(int x, int y, boolean multiple) {
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			JSONObject jsonCoord = new JSONObject();
			jsonCoord.put("x", x);
			jsonCoord.put("y", y);
			json.get().put("strategy", "COORDINATES");
			json.get().put("selector", jsonCoord.toString());
			json.get().put("multiple", multiple);
			
			addService(json);
		} catch(Exception e) {}
	}
	
	public final static String[] STRATEGIES = {
		"CLASS_NAME",	// class
		"NAME", 		// content-desc
		"ID",			// resource-id
		"XPATH",
		"COORDINATES"
	};
}
