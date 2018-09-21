package com.hyundai.autoever.mirror.engine;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.mirror.engine.io.*;
import com.hyundai.autoever.mirror.engine.query.BaseDeviceScript;
import com.hyundai.autoever.mirror.engine.query.InitCommand;
import com.hyundai.autoever.mirror.engine.query.RotationScript;
import com.hyundai.autoever.mirror.engine.query.SendKeyScript;
import com.hyundai.autoever.mirror.engine.query.TouchScript;
import com.hyundai.autoever.utils.AnsiLog;


public class CommandServer {
	private static final Logger LOG = Logger.getLogger(CommandServer.class);

	private static final int PORT = 12502;
	
	private ServerModule mModule = null;
	private boolean isRunning = false;
	private ExecutorService mConnectionThreadPool = null;
	
	public CommandServer(ExecutorService pool) {
		mModule = ServerModule.get();
		mConnectionThreadPool = pool;
	}
	
	public void start() {
		try {
			// 서버소켓 생성
			ServerSocket serverSocket = new ServerSocket(PORT);
			isRunning = true;

			AnsiLog.h("[INFO] Listening on COMMAND_AGENT_PORT " + PORT);
			
			// 소켓서버가 종료될때까지 무한루프
			while(isRunning()){
				// 소켓 접속 요청이 올때까지 대기합니다.
				Socket socket = serverSocket.accept();
				try{
					// 요청이 오면 스레드 풀의 스레드로 소켓을 넣어줍니다.
					// 이후는 스레드 내에서 처리합니다.
					mConnectionThreadPool.execute(new ConnectionCommandWrap(socket));
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		isRunning = false;
		try { Thread.sleep(1); } catch (InterruptedException e) {}
		
		if(mConnectionThreadPool != null) {
			try {
	    	    mConnectionThreadPool.shutdown();
	    	    mConnectionThreadPool.awaitTermination(2000, TimeUnit.MILLISECONDS);
	    	}
	    	catch (InterruptedException e) {
	    	}
	    	finally {
	    	    if (!mConnectionThreadPool.isTerminated()) {
	    	        mConnectionThreadPool.shutdownNow();
	    	    }
	    	}
		}
		AnsiLog.h("[INFO] Terminates on COMMAND_AGENT_PORT " + PORT);
	}
	
	public boolean isRunning() {
		return isRunning;
	}
	
	public ADB getADB() {
		return mModule.getADB();
	}
	
	public ConcurrentMap<String, MirrorEngine> getEngines() {
		return mModule.getEngines();
	}
	
	public class ConnectionCommandWrap implements Runnable, AndroidScreenObserver {

		private Socket mSocket = null;
		
		private JSONObject mJsonBaseDev = null;

		private ConcurrentMap<String, MirrorEngine> mEngines = null; 
		private String mDeviceModels = "";
		private Window mBaseDisplay = null;
		
		private RemoteMessageWriter writer = null;
		private RemoteMessageRouter router = null;
		private RemoteMessageRegister register = null;

        private Object mLockData = new Object();
		
		public ConnectionCommandWrap(Socket socket) {
			this.mSocket = socket;
			
			this.mEngines = new ConcurrentHashMap<String, MirrorEngine>();
		}

		@Override
		public void run() {
			Thread thread = Thread.currentThread();
			
			try{
				writer = new RemoteMessageWriter(this, mSocket.getOutputStream());
				register = new RemoteMessageRegister(this, writer);
				router = register.getRouter();
				
				BufferedReader streamReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), "UTF-8"));
				while (isRunning() && !thread.isInterrupted() && mSocket.isConnected()) {
					String inputStr = streamReader.readLine();
			        if(inputStr != null && inputStr.length() > 0) {
			        	JSONObject envelope = new JSONObject(inputStr);
			        	String method = envelope.getString("method");
			        	if(method.compareToIgnoreCase("exit") == 0) {
			        		break;
			        	}
			        	else if(router.isRegister(method)) {
			            	AnsiLog.d("Envelope : " + envelope.toString());
			        		router.route(envelope);
			        	}
			        }
			        else {
			        	Thread.sleep(100);
			        }
				}
                
			} catch(InterruptedException e){
				e.printStackTrace();
			} catch(IOException e){
				e.printStackTrace();
			} catch (JSONException e) {
                e.printStackTrace();
            } finally {
   				AnsiLog.i2(2009, "[" + mDeviceModels + "] " + "The Command Devices STOP.");
				
            	if (router != null) {
                    router.cleanup();
                    router = null;
                }
            	
    			try {
    				mSocket.close();
    				mSocket = null;
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
            	writer = null;
            	register = null;
            	mEngines = null;
            	mBaseDisplay = null;
            	mJsonBaseDev = null;
            	mLockData = null;

    			System.gc();
            }
			
		}
		
		public Socket getSocket() {
			return this.mSocket;
		}
		
		public RemoteMessageWriter getWriter() {
			return writer;
		}
		
		public Object getLockData() {
			return mLockData;
		}
		
		public MirrorEngine findEngine(String serial) {
			serial = serial.toLowerCase();
			if(mEngines.containsKey(serial))
				return mEngines.get(serial);
			return null;
		}
		
		public void clearEngines() {
			mEngines.clear();
			mDeviceModels = "";
		}
		
		public void addEngine(MirrorEngine engine) {
			mEngines.put(engine.getDeviceSerial().toLowerCase(), engine);
			
			if(mDeviceModels.length() > 0)
				mDeviceModels += ",";
			
			mDeviceModels += engine.getDeviceModel();
			
			AnsiLog.i2(2001, "[" + mDeviceModels + "] " + "A Command Device(" + engine.getDeviceName() + ") Added.");
		}
		
		public ConcurrentMap<String, MirrorEngine> getEngines() {
			return mEngines;
		}
		
		public MirrorEngine getEngine(String serial) {
			serial = serial.toLowerCase();
			if(mEngines.containsKey(serial))
				return mEngines.get(serial);
			return null;
		}
		
		public MirrorEngine getBaseEngine() {
			String serial = null;
			try {
				serial = mJsonBaseDev.getString("serial");
			} catch(JSONException e) {}
			
			if(serial == null)
				return null;
			
			return getEngine(serial);
		}

		public JSONObject getBaseDevice() {
			return mJsonBaseDev;
		}

		public Window getBaseDisplay() {
			return mBaseDisplay;
		}

		public String getDeviceModels() {
			return mDeviceModels;
		}
		
		public boolean setBaseDevice(JSONObject jsonDev) {
			Window window = new Window(jsonDev);
			if(window.getSerial().isEmpty() || window.getDpiX() == 0 || window.getDpiY() == 0)
				return false;

			mJsonBaseDev = jsonDev;
			mBaseDisplay = window;
			return true;
		}
		
		@Override
		public void initWindow(Window window) {
		}
		@Override
		public void initBanner(Banner banner) {
		}
		@Override
		public void frameImage(SoftReference<BufferedImage> image) {
		}
		@Override
		public void release() {
		}
	}
	

}
