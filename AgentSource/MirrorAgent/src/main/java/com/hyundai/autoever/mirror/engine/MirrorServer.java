package com.hyundai.autoever.mirror.engine;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.mirror.engine.query.AbstractRemoteCommand;
import com.hyundai.autoever.utils.AnsiLog;
import com.hyundai.autoever.utils.ImageUtil;

public class MirrorServer {
	private static final Logger LOG = Logger.getLogger(MirrorServer.class);

	private static final int PORT = 12501;
	
	private static final byte MST_STREAM = 1;
	private static final byte MST_JSON = 2;
	
	private ServerModule mModule = null;
	private boolean isRunning = false;
	private ExecutorService mConnectionThreadPool = null;
	
	public MirrorServer(ExecutorService pool) {
		mModule = ServerModule.get();
		mConnectionThreadPool = pool;
	}
	
	public void start() {
		try {
			// 서버소켓 생성
			ServerSocket serverSocket = new ServerSocket(PORT);
			isRunning = true;

			AnsiLog.h("[INFO] Listening on MIRROR_AGENT_PORT " + PORT);
			
			// 소켓서버가 종료될때까지 무한루프
			while(isRunning()){
				// 소켓 접속 요청이 올때까지 대기합니다.
				Socket socket = serverSocket.accept();
				try{
					// 요청이 오면 스레드 풀의 스레드로 소켓을 넣어줍니다.
					// 이후는 스레드 내에서 처리합니다.
					mConnectionThreadPool.execute(new ConnectionWrap(socket));
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
		AnsiLog.h("[INFO] Terminates on MIRROR_AGENT_PORT " + PORT);
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
	
	class ConnectionWrap implements Runnable, AndroidScreenObserver {

		private Socket mSocket = null;
		private JSONObject mJsonObject = null;
		private String mMethod = "init";
		private String mDeviceName = "";
		private String mDeviceSerial = "";
		private String mDeviceModel = "";
		private InputMirrorClient mInClient = null;
		private OutputMirrorClient mOutClient = null;
		private MirrorEngine mEngine = null;
		private AnsiLog mLog = null;
		private Object mLockData = new Object();
		private Object mLockOut = new Object();
		private volatile boolean isRunClient = true;
		private final int MAX_FRAMES = 30;
		private Queue<SoftReference<BufferedImage>> mImageQueue = new LinkedBlockingQueue<SoftReference<BufferedImage>>();
		
		private int mWidth = 240, mHeight = 320, mFps = 30;

		public ConnectionWrap(Socket socket) {
			this.mSocket = socket;
			this.mLog = new AnsiLog();
		}

		@Override
		public void run() {
			Thread thread = Thread.currentThread();
			MirrorEngine engine = null;
			
			try{
				InputStream inputStream = mSocket.getInputStream();
				BufferedReader input = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
				String inputStr = "";
				
				if((inputStr = input.readLine()) != null && inputStr.length() > 0) {
					try {
						mJsonObject = new JSONObject(inputStr);
					} catch(JSONException e){
						e.printStackTrace();
					}
				}
				input = null;
			} catch(IOException e){
				e.printStackTrace();
			}
			
			if(mJsonObject == null || !mJsonObject.has("serial")) {
				mLog.error(9000, "The requested json information from the client is not correct.");
			}
			else {
				try {
					mMethod = mJsonObject.getString("method");
					mDeviceModel = mJsonObject.getString("model");
					mDeviceName = mJsonObject.getString("name");
					mDeviceSerial = mJsonObject.getString("serial");
				} catch(JSONException e) {}
					
				engine = mModule.findEngine(mDeviceSerial);
				if(engine == null) {
					mLog.error(9001, "[" + mDeviceModel + "] " + "The Mirror Device(" + mDeviceSerial + ") NOT exist.");
				}
				else
					AnsiLog.i2(1004, "[" + mDeviceModel + "] " + "The Mirror Device(" + mDeviceSerial + ") is already prepared.");
			}
			
			if(engine != null) {
				engine.initWindow();
				
				synchronized(mLockData) {
					mEngine = engine;
					mWidth = engine.getWindow().getAppWidth();
					mHeight = engine.getWindow().getAppHeight();
				}
				
				setRequestJson(mJsonObject);
				if(!engine.isRunning()) {
					AnsiLog.d("[" + mDeviceModel + "] " + "The Mirror Device (" + mDeviceSerial + ") Starting.");
					engine.start();
				}
				engine.registerObserver(this);
				
				AnsiLog.i2(1002, "[" + mDeviceModel + "] " + "The Mirror Device (" + mDeviceSerial + ") Started.");
				
				response_success();
				
				ExecutorService execService = Executors.newCachedThreadPool();
				
				mInClient = new InputMirrorClient(this);
				mOutClient = new OutputMirrorClient(this);
				
				useFinalImage();
				
				execService.submit(mInClient);
				execService.submit(mOutClient);
				
				waitEngine(engine);
				
				try {
					execService.shutdown();
					execService.awaitTermination(2000, TimeUnit.MILLISECONDS);
		    	}
		    	catch (InterruptedException e) {
		    	}
		    	finally {
		    	    if (!execService.isTerminated()) {
		    	    	execService.shutdownNow();
		    	    }
		    	}
			}
			
			if(mLog.getErrorCode() > 0)
				response_fail();
				
			try {
				mSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if(engine != null) {
				AnsiLog.i2(1003, "[" + mDeviceModel + "] " + "The Mirror Device(" + mDeviceSerial + ") STOP.");
				
				engine.removeObserver(this);
				if(engine.getObserverCount() == 0) {
					engine.stop();
				}
			}
			
			synchronized(mLockData) {
				mEngine = null;
			}
			
			mInClient = null;
			mOutClient = null;
			mJsonObject = null;
			mLockData = null;
			mLog = null;
			System.gc();
		}
		
		private void waitEngine(MirrorEngine engine) {
			try {
				while(isRunClient() && mSocket.isConnected()) {
					if(engine.isRunning() && engine.isCollecting()) {
						Thread.sleep(1000);
					}
					Thread.sleep(100);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		public void clear_error() {
			mLog.clearError();
		}
		
		public void response_success() {
			JSONObject json = AbstractRemoteCommand.success(mMethod, getEngine().getDeviceSerial(), getEngine().getWindow().toJson());
			String result = json.toString()+"\n";
			try {
				response(result);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		public void response_fail() {
			JSONObject json = AbstractRemoteCommand.failed(mMethod, getEngine().getDeviceSerial(), mLog.getErrorMsg(), mLog.getErrorCode());
			String result = json.toString()+"\n";
			try {
				response(result);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		public void response(String msg) throws Exception {
			byte[] byteType = {MST_JSON};
			byte[] result = msg.getBytes(Charset.forName("UTF-8")); 
			byte[] byteSize = MirrorEngine.toLittleEndianArray(result.length);
			
			OutputStream outputStream = mSocket.getOutputStream();
			BufferedOutputStream output = new BufferedOutputStream(outputStream);

			synchronized(mLockOut) {
				output.write(byteType);
				output.write(byteSize);
				output.write(result);
				output.flush();
			}
			output = null;
		}
		
		public void reponse(byte[] result) throws Exception {
			OutputStream outputStream = mSocket.getOutputStream();
			BufferedOutputStream output = new BufferedOutputStream(outputStream);

			reponse(result, output);
			output = null;
		}
		
		public void reponse(byte[] result, BufferedOutputStream output) throws Exception {
			byte[] byteType = {MST_STREAM};
			byte[] byteSize = MirrorEngine.toLittleEndianArray(result.length);
			
			synchronized(mLockOut) {
				output.write(byteType);
				output.write(byteSize);
				output.write(result);
				output.flush();
			}
			output = null;
		}
		
		public Socket getSocket() {
			return this.mSocket;
		}
		
		public Object getLockData() {
			return mLockData;
		}
		
		public Object getLockOut() {
			return mLockOut;
		}
		
		public void setMethod(String method) {
			mMethod = method;
		}
		
		public MirrorEngine getEngine() {
			synchronized(mLockData) {
				return mEngine;
			}			
		}
		
		public String getJsonValue(String key) {
			if(mJsonObject == null)
				return "";
			
			String value = "";
			try {
				value = mJsonObject.getString(key);
			} catch(JSONException e) {}
			return value;
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
		
		public void stopClient() {
			isRunClient = false;
		}
		
		public boolean isRunClient() {
			if(isRunning()) {
				return isRunClient;
			}
			return false;
		}
		
		public void setRequestJson(JSONObject jsonReq) {
			MirrorEngine engine = getEngine();
			if(jsonReq.has("rotation")) {
				String sRotation = "ROTATION_0";
				try {
					jsonReq.getString("rotation");
				} catch(JSONException e) {}
				if(engine != null) {
					if(engine.getRotationValue(sRotation) != engine.getWindow().getRotation())
						engine.executeRotation(sRotation);
				}
			}
			if(jsonReq.has("width") && jsonReq.has("height")) {
				String sWidth = "", sHeight = "";
				try {
					sWidth = jsonReq.getString("width");
					sHeight = jsonReq.getString("height");
				} catch(JSONException e) {}
				setRequestResizeImage(sWidth, sHeight);
			}
			if(jsonReq.has("fps")) {
				int fps = 30;
				try {
					fps = jsonReq.getInt("fps");
				} catch(JSONException e) {}
				setRequestFps(fps);
			}
			if(jsonReq.has("enable")) {
				boolean enable = true;
				try {
					enable = jsonReq.getBoolean("enable");
				} catch(JSONException e) {}
				if(engine != null)
					engine.setDeviceEnable(enable);
			}
			if(jsonReq.has("visible")) {
				boolean visible = true;
				try {
					visible = jsonReq.getBoolean("visible");
				} catch(JSONException e) {}
				if(!visible)
					clearQueueImage();
				if(engine != null)
					engine.setDeviceVisible(visible);
			}
		}
		
		public void setRequestResizeImage(String sWidth, String sHeight) {
			if(sWidth == null || sHeight == null)
				return;
			
			int w, h, iw, ih, r = 0;
			synchronized(mLockData) {
				iw = w = mEngine.getWindow().getPhysicalWidth();
				ih = h = mEngine.getWindow().getPhysicalHeight();
				r = mEngine.getWindow().getRotation();
			}
			sWidth = sWidth.trim(); sHeight = sHeight.trim();
			if(sWidth.endsWith("%")) {
				sWidth = sWidth.replaceAll("%", "");
				sWidth = sWidth.trim();
				try {
					w = (int)((float)w*(float)Integer.valueOf(sWidth)/100.0f);
				} catch(NumberFormatException e) {}
			}
			else if(sWidth.endsWith("px")) {
				sWidth = sWidth.replaceAll("px", "");
				sWidth = sWidth.trim();
				try {
					w = Integer.valueOf(sWidth);
				} catch(NumberFormatException e) {}
			}
			else {
				sWidth = sWidth.trim();
				try {
					w = Integer.valueOf(sWidth);
				} catch(NumberFormatException e) {}
			}
			
			if(sHeight.endsWith("%")) {
				sHeight = sHeight.replaceAll("%", "");
				sHeight = sHeight.trim();
				try {
					h = (int)((float)h*(float)Integer.valueOf(sHeight)/100.0f);
				} catch(NumberFormatException e) {}
			}
			else if(sHeight.endsWith("px")) {
				sHeight = sHeight.replaceAll("px", "");
				sHeight = sHeight.trim();
				try {
					h = Integer.valueOf(sHeight);
				} catch(NumberFormatException e) {}
			}
			else {
				sHeight = sHeight.trim();
				try {
					h = Integer.valueOf(sHeight);
				} catch(NumberFormatException e) {}
			}
			if(w != iw || h != ih) {
				int tw = 0, th = 0;
				float wd, hd;

				if((iw > ih && w < h) || (iw < ih && w > h)) {
					int t = w;
					w = h;
					h = t;
				}
				
				wd = (float)w / iw;
				hd = (float)h / ih;
				if(wd < hd) {
					tw = w;
					th = (int)(ih * wd);
				}
				else {
					th = h;
					tw = (int)(iw * hd);
				}
				w = tw; h = th;
			}
			
			AnsiLog.i2(1010, "[" + mDeviceModel + "] " + "Screen Width(" + w + ") * Height(" + h + ") Mirror Device " + mDeviceName);
			synchronized(mLockData) {
				mWidth = w;
				mHeight = h;
			}
		}
		
		public void setRequestFps(int fps) {
			AnsiLog.i2(1010, "[" + mDeviceModel + "] " + "FPS(" + fps + ") Mirror Device " + mDeviceName);
			synchronized(mLockData) {
				mFps = fps;
			}
		}
		
		public int getWidth() {
			synchronized(mLockData) {
				return mWidth;
			}
		}
		
		public int getHeight() {
			synchronized(mLockData) {
				return mHeight;
			}
		}
		
		public int getFps() {
			synchronized(mLockData) {
				return mFps;
			}
		}
		
		public boolean isDeviceVisible() {
			MirrorEngine engine = getEngine();
			if(engine == null)
				return false;
			return engine.isDeviceVisible();
		}

		public boolean isEmptyQueueImage() {
			return mImageQueue.isEmpty();
		}

		public void removeNoUseQueueImage() {
			//synchronized(mLockData)
			{
				int cnt = mImageQueue.size();
				for(int i=0;i<cnt-1;i++) {
					SoftReference<BufferedImage> image = mImageQueue.remove();
					image.clear();
				}
			}
		}
		
		public void clearQueueImage() {
			//synchronized(mLockData)
			{
				for(int i=0;i<mImageQueue.size();i++) {
					SoftReference<BufferedImage> image = mImageQueue.remove();
					image.clear();
				}
				mImageQueue.clear();
			}
		}
		
		public SoftReference<BufferedImage> getQueueImage() {
			return mImageQueue.poll();
		}
		
		public void useFinalImage() {
			if(isEmptyQueueImage()) {
				SoftReference<BufferedImage> image = getEngine().getCloneFinalImage();
				if(image != null) {
					if(mOutClient != null)
						mOutClient.noSkip();
					frameImage(image);
					image.clear();
				}
			}
		}
		
		@Override
		public void initWindow(Window window) {
		}
		@Override
		public void initBanner(Banner banner) {
		}
		@Override
		public void frameImage(SoftReference<BufferedImage> image) {
			if(!isDeviceVisible())
				return;
			//synchronized(mLockData)
			{
				if(mImageQueue.size() > MAX_FRAMES) {
					SoftReference<BufferedImage> imgPoll = mImageQueue.remove();
					imgPoll.clear();
				}
				mImageQueue.add(ImageUtil.cloneImageRef(image.get()));
				//mImageQueue.add(image);
			}
		}
		@Override
		public void release() {
		}
	}
	
	class InputMirrorClient implements Runnable {

		private ConnectionWrap wrap = null;
		private Socket socket = null;

		public InputMirrorClient(ConnectionWrap wrap) {
			this.wrap = wrap;
			this.socket = wrap.getSocket();
		}

		@Override
		public void run() {
			Thread thread = Thread.currentThread();
			try {
				AnsiLog.i2(1020, "[" + this.wrap.getDeviceModel() + "] " + "Running a read stream.");
				
				InputStream inputStream = socket.getInputStream();
				
				while (wrap.isRunClient() && !thread.isInterrupted()) {
					BufferedReader input = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
					String inputStr = "";
					JSONObject jsonObject = null;
					
					if((inputStr = input.readLine()) != null && inputStr.length() > 0) {
						try {
							jsonObject = new JSONObject(inputStr);
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
					else if(inputStr == null) {
						break;
					}
					
					wrap.clear_error();
					if(jsonObject != null) {
						String method = jsonObject.getString("method");
						if(method.compareToIgnoreCase("exit") == 0)
							break;
						wrap.setMethod(method);
						wrap.setRequestJson(jsonObject);
						if(jsonObject.has("resize"))
							wrap.useFinalImage();
						wrap.response_success();
						
						jsonObject = null;
					}
					else {
						wrap.response_fail();
					}
					Thread.sleep(1);
				}

			} catch (IOException e) {
				//e.printStackTrace();
			} catch (Exception e) {
				//e.printStackTrace();
			} finally {
				wrap.stopClient();
				wrap = null;
				
				System.gc();
				AnsiLog.i2(1120, "[" + this.wrap.getDeviceModel() + "] " + "Finally a read stream.");
			}
		}
	}
	
	class OutputMirrorClient implements Runnable {

		private ConnectionWrap wrap = null;
		private Socket socket = null;
		private volatile boolean noSkip = false;

		public OutputMirrorClient(ConnectionWrap wrap) {
			this.wrap = wrap;
			this.socket = wrap.getSocket();
		}
		
		public void noSkip() {
			noSkip = true;
		}

		@Override
		public void run() {
			Thread thread = Thread.currentThread();
			try {
				AnsiLog.i2(1030, "[" + this.wrap.getDeviceModel() + "] " + "Running a write stream.");
				
				OutputStream outputStream = socket.getOutputStream();
				BufferedOutputStream output = new BufferedOutputStream(outputStream);
				
				SoftReference<byte[]> binary = null;
				int c, iw, ih, tw, th;
				int w = wrap.getWidth(), h = wrap.getHeight(), fps = wrap.getFps();
				long y = 1000, m = (int)(1000.0f/(float)fps);
				long start = System.currentTimeMillis(), now = 0, s = 0;
				while (wrap.isRunClient() && !thread.isInterrupted()) {
					if (wrap.isEmptyQueueImage()) {
						Thread.sleep(1);
						continue;
					}
					if(!wrap.isDeviceVisible()) {
						Thread.sleep(10);
						continue;
					}
					tw = w = wrap.getWidth();
					th = h = wrap.getHeight();
					fps = wrap.getFps();
					m = (int)(1000.0f/(float)fps);
					
					SoftReference<BufferedImage> image = wrap.getQueueImage();
					
					now = System.currentTimeMillis();
					if(!noSkip && now-start < m) {
						now = System.currentTimeMillis();
						if(now-start < m) {
							s = m-(now-start);
							//AnsiLog.d("[" + this.wrap.getDeivceIdx() + "] " + "write stream fps: " + fps + ", ms:" + m + ", sleep:" + s);
							Thread.sleep(s);
						}
						wrap.removeNoUseQueueImage();
						image.clear();
						System.gc();
					}
					else {
						noSkip = false;
						iw = image.get().getWidth();
						ih = image.get().getHeight();
						if(iw > ih) {
							w = Math.max(tw, th);
							h = Math.min(tw, th);
						}
						else {
							w = Math.min(tw, th);
							h = Math.max(tw, th);
						}
						if(ServerModule.get().isUseOpengl()) {
							binary = ImageUtil.opencv_resizeByteFromImage(image.get(), w, h);
						}
						else {
							SoftReference<BufferedImage> imageResize = null;
							if(iw != w || ih != h) {
								imageResize = ImageUtil.resizeImageRef(image.get(), w, h);
								image = imageResize;
							}
							binary = ImageUtil.createByteFromImageRef(image.get());
							
							if(imageResize != null)
								imageResize.clear();
						}
						//binary = ImageUtil.createByteFromImageRef(image.get());
						
						//AnsiLog.d("[" + this.wrap.getDeviceModel() + "] " + "send image w*h : " + w + ", " + h);
						//image = null;
						if(binary != null && binary.get() != null) {
							wrap.reponse(binary.get(), output);
							//binary = null;
						}
						binary.clear();
						if(now-start > y)
							Thread.sleep(1);
						start = now;
						//start = System.currentTimeMillis();
					}
				}

			} catch (IOException e) {
				//e.printStackTrace();
			} catch (InterruptedException e) {
				//e.printStackTrace();
			} catch (Exception e) {
				//e.printStackTrace();
			} finally {
				wrap.clearQueueImage();
				wrap.stopClient();
				wrap = null;
				
				System.gc();
				AnsiLog.i2(1130, "[" + this.wrap.getDeviceModel() + "] " + "Finally a write stream.");
			}
		}
		
	}
}
