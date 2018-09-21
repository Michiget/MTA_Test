package com.hyundai.autoever.mirror.engine;

import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.SoftReference;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.io.RemoteMessageWriter;
import com.hyundai.autoever.mirror.engine.query.AbstractRemoteCommand;
import com.hyundai.autoever.utils.AnsiLog;

public class ClientService implements Runnable {
    public static final String IME_ADB_KBD = "com.bq.adbhw";
    public static final String IME_SW_KBD = IME_ADB_KBD + "/." + "SoftKeyboard";
    public static final String IME_MESSAGE = IME_ADB_KBD + "." + "ADB_INPUT_TEXT";
    public static final String IME_CHARS = IME_ADB_KBD + "." + "ADB_INPUT_CHARS";
    public static final String IME_KEYCODE = IME_ADB_KBD + "." + "ADB_INPUT_CODE";
    public static final String IME_EDITORCODE = IME_ADB_KBD + "." + "ADB_EDITOR_CODE";

    private final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	
    private ExecutorService mExecutorService = null;
	private MirrorEngine mEngine = null;
	private Queue<SoftReference<JSONObject>> mKbdCmdQueue = new LinkedBlockingQueue<SoftReference<JSONObject>>();
	private Queue<SoftReference<JSONObject>> mAgentCmdQueue = new LinkedBlockingQueue<SoftReference<JSONObject>>();
	private Queue<SoftReference<JSONObject>> mServiceCmdQueue = new LinkedBlockingQueue<SoftReference<JSONObject>>();
	private ConcurrentHashMap<String, ServiceCommander> mServiceCmdMap = new ConcurrentHashMap<String, ServiceCommander>();
	private volatile boolean isRunning = false;
	
	public ClientService(MirrorEngine engine) {
		mEngine = engine;
		isRunning = true;
		
		mExecutorService = Executors.newFixedThreadPool(3);
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
		mAgentCmdQueue.clear();
		mServiceCmdQueue.clear();
		mKbdCmdQueue.clear();
	}
	
	@Override
	public void run() {
		Thread thread = Thread.currentThread();
		
		mExecutorService.submit(new ClientAgent());
		//mExecutorService.submit(new ClientKeyboard());
		mExecutorService.submit(new ClientMonitor());
		
		while(isRunning && mEngine.isRunning() && !thread.isInterrupted()) {
			try {
				Thread.sleep(2000);
			} catch(Exception e) {}
		}
	}
	
	class ClientAgent implements Runnable {
		private int retryConn = 0;
		private boolean isRun = true;
		
		public boolean isRunning() {
			return isRunning && isRun && mEngine.isRunning() && !Thread.currentThread().isInterrupted();
		}
		
		@Override
		public void run() {
			while(isRunning()) {
				Socket socket = null;
				BufferedOutputStream mStreamOutput = null;
				try {
					socket = new Socket("localhost", mEngine.AGENT_PORT);
					mStreamOutput = new BufferedOutputStream(socket.getOutputStream());
					
					while(isRunning()) {
						if(mAgentCmdQueue.isEmpty()) {
							Thread.sleep(1);
							continue;
						}
						
						SoftReference<JSONObject> obj = mAgentCmdQueue.poll();
						String sCmd = obj.get().toString();
						AnsiLog.d("Agent cmd : " + sCmd);
						byte[] cmd = encodeUTF8(sCmd + "\n");
						mStreamOutput.write(cmd);
						mStreamOutput.flush();
						obj.clear();
						
						try { Thread.sleep(1); } catch (InterruptedException e) {}
					}
				} catch(Exception e) {
					if(isRunning()) {
						retryConn++;
						if(retryConn >= MirrorEngine.MAX_RETRY_CONNECT) {
							isRun = false;
						}
					}
				} finally {
					if (mStreamOutput != null) {
						try {
							mStreamOutput.close();
							mStreamOutput = null;
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

	class ClientMonitor implements Runnable {
		private int retryConn = 0;
		private boolean isRun = true;
		private AnsiLog log = new AnsiLog();
		
		public ClientMonitor() {
			
		}
		
		public boolean isRunning() {
			return isRunning && isRun && mEngine.isRunning() && !Thread.currentThread().isInterrupted();
		}
		
		@Override
		public void run() {
			while(isRunning()) {
				Socket socket = null;
				BufferedWriter streamWriter = null;
				BufferedReader streamReader = null;
				try {
					socket = new Socket("localhost", mEngine.SERVICE_PORT);
					
					streamWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
					streamReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
					while (isRunning() && socket.isConnected()) {
						String inputStr = null;
						if(streamReader.ready()) {
							inputStr = streamReader.readLine();
							respond(inputStr);
						}
						if(!mServiceCmdQueue.isEmpty()) {
							SoftReference<JSONObject> obj = mServiceCmdQueue.poll();
							String sCmd = obj.get().toString();
							AnsiLog.d("Service cmd : " + sCmd);
							streamWriter.write(sCmd + "\n");
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
						if(retryConn >= MirrorEngine.MAX_RETRY_CONNECT) {
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
		
		public void respond(String inputStr) {
			if(inputStr == null || inputStr.isEmpty())
				return;
			String method = "";
			try {
				JSONObject envelope = new JSONObject(inputStr);
	        	String type = envelope.getString("type");
	        	if(type.equals("rotation")) {
	        		if(envelope.has("rotation"))
	        			mEngine.setRotation(envelope.getInt("rotation"));
	        	}
	        	else if(envelope.has("uuid")) {
	        		String uuid = envelope.getString("uuid");
	        		if(mServiceCmdMap.containsKey(uuid)) {
	        			int error_code = 0;
	        			boolean doResult = false, isMain = false, isBaseEngine = false;
	        			ServiceCommander svcCmd = mServiceCmdMap.get(uuid);
	        			RemoteMessageWriter writer = svcCmd.writer;
	        			ScriptRunner runner = svcCmd.runner;
	        			JSONObject jsonReq = svcCmd.jsonReq;
		        		
		        		if(type.equals("getFocus"))
		        			error_code = 9151;
		        		else if(type.equals("getCache"))
		        			error_code = 9152;
		        		else if(type.equals("find"))
		        			error_code = 9153;
		        		else if(type.equals("click"))
		        			error_code = 9154;
		        		else if(type.equals("getText"))
		        			error_code = 9155;
		        		else if(type.equals("setText"))
		        			error_code = 9156;
		        		else if(type.equals("clearText"))
		        			error_code = 9157;
		        		
		        		method = jsonReq.getString("method");
	        			if(jsonReq.has("only_main")) {
	        				isMain = jsonReq.getBoolean("only_main");
	        				if(!isMain && jsonReq.has("with_main"))
	        					isMain = jsonReq.getBoolean("with_main");
	        			}
	        			else if(jsonReq.has("with_main"))
	        				isMain = jsonReq.getBoolean("with_main");
		        		doResult = jsonReq.has("scriptidx");
		        		isBaseEngine = runner.isBaseEngine();
		        		
		        		if(error_code > 0) {
			        		if(envelope.getBoolean("success")) {
			        			JSONObject json = envelope.getJSONObject("data");
			        			jsonReq.put("element", json);
			        			if(doResult || (isMain && isBaseEngine))
			        				writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), jsonReq));
			        			runner.addResultOK(jsonReq);
			        		}
			        		else {
			        			String errMsg = "The element could not be found.";
			        			if(envelope.has("error_msg")) {
			        				errMsg = envelope.getString("error_msg");
			        				log.error(error_code, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + errMsg);
			        			}
			        			else {
			        				log.error(error_code, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + errMsg);
			        			}
								writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), jsonReq, log.getErrorMsg(), log.getErrorCode()));
								runner.addResult(log.getErrorCode(), log.getErrorMsg(), jsonReq);
			        		}
		        		}
		        		mServiceCmdMap.remove(uuid);
	        		}
	        	}
			} catch(JSONException e) {}
		}
	}

	public void addAgent(JSONObject json) {
		mAgentCmdQueue.add(new SoftReference<JSONObject>(json));
	}
	
	public void addAgent(SoftReference<JSONObject> json) {
		mAgentCmdQueue.add(json);
	}

	public void addService(JSONObject json) {
		mServiceCmdQueue.add(new SoftReference<JSONObject>(json));
	}
	
	public void addService(SoftReference<JSONObject> json) {
		mServiceCmdQueue.add(json);
	}

	public void addKbd(JSONObject json) {
		mKbdCmdQueue.add(new SoftReference<JSONObject>(json));
	}
	
	public void addKbd(SoftReference<JSONObject> json) {
		mKbdCmdQueue.add(json);
	}
	
	public void addDoKeyDown(int keyCode, boolean shift, boolean alt, boolean ctrl, boolean capsLock, boolean numLock, boolean scrollLock) {
		int keyCodeAndroid = toAndroidKeyCode(keyCode);
		AnsiLog.d("keyCode : " + keyCode + ", keyCodeAndroid: " + keyCodeAndroid + ", shift : " + shift + ", alt : " + alt + ", ctrl : " + ctrl);
		if(keyCodeAndroid < 0)
			return;
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", "do_key");
			json.get().put("event", "down");
			json.get().put("keycode", keyCodeAndroid);
			json.get().put("shift", shift);
			json.get().put("alt", alt);
			json.get().put("ctrl", ctrl);
			json.get().put("capsLock", capsLock);
			json.get().put("numLock", numLock);
			json.get().put("scrollLock", scrollLock);
			addAgent(json);
		} catch(Exception e) {}
	}
	
	public void addDoKeyUp(int keyCode, boolean shift, boolean alt, boolean ctrl, boolean capsLock, boolean numLock, boolean scrollLock) {
		int keyCodeAndroid = toAndroidKeyCode(keyCode);
		if(keyCodeAndroid < 0)
			return;
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", "do_key");
			json.get().put("event", "up");
			json.get().put("keycode", keyCodeAndroid);
			json.get().put("shift", shift);
			json.get().put("alt", alt);
			json.get().put("ctrl", ctrl);
			json.get().put("capsLock", capsLock);
			json.get().put("numLock", numLock);
			json.get().put("scrollLock", scrollLock);
			addAgent(json);
		} catch(Exception e) {}
	}
	
	public void addDoKeyPress(int keyCode, boolean shift, boolean alt, boolean ctrl, boolean capsLock, boolean numLock, boolean scrollLock) {
		int keyCodeAndroid = toAndroidKeyCode(keyCode);
		if(keyCodeAndroid < 0)
			return;
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", "do_key");
			json.get().put("event", "press");
			json.get().put("keycode", keyCodeAndroid);
			json.get().put("shift", shift);
			json.get().put("alt", alt);
			json.get().put("ctrl", ctrl);
			json.get().put("capsLock", capsLock);
			json.get().put("numLock", numLock);
			json.get().put("scrollLock", scrollLock);
			addAgent(json);
		} catch(Exception e) {}
	}
	
	public boolean addDoKey(JSONObject json) {
		int keyCode = -1;
		String[] sFunc = {"shift", "alt", "ctrl", "capsLock", "numLock", "scrollLock"};
		boolean[] func = new boolean[sFunc.length];

		for(int i =0;i<func.length;i++)
			func[i] = false;
		
		try {
			if(json.has("keycode"))
				keyCode = json.getInt("keycode");
			for(int i =0;i<func.length;i++) {
				if(json.has(sFunc[i]))
					func[i] = json.getBoolean(sFunc[i]);
			}
		} catch(Exception e) {}
		
		if(keyCode < 0)
			return false;
		
		addDoKeyPress(keyCode, func[0], func[1], func[2], func[3], func[4], func[5]);
		return true;
	}
	
	public boolean addDoKeys(JSONArray jsonKeys) {
		try {
			for(int i=0;i<jsonKeys.length();i++) {
				addDoKey(jsonKeys.getJSONObject(i));
				Thread.sleep(80);
			}
		} catch(Exception e) {}
		return true;
	}
	
	public boolean addDoText2(String text) {
		if(text.length() == 0)
			return false;
		
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", "do_text");
			json.get().put("text", text);
			addKbd(json);
			
			return true;
		} catch(Exception e) {}
		return false;
	}
		
	public boolean addDoText(String text) {
		if(text.length() == 0)
			return false;
		
		char[] chars = text.toCharArray();
		String inputChars = "";
		
		for(int i=0;i<chars.length;i++) {
			if(!inputChars.isEmpty())
				inputChars += ",";
			inputChars += String.format("%d", (int)chars[i]);
		}
		
		String output;
        //String cmd = "am broadcast -a " + IME_MESSAGE + " --es msg '" + text + "'";
        String cmd = "am broadcast -a " + IME_CHARS + " --eia chars '" + inputChars + "'";
        output = mEngine.executeShellCommand(cmd);
        return true;
	}
	
//	public void addDoText3(String text) {
//		List<UnicodeText> texts = UnicodeKorean.convertEngFromKor(text);
//		if(!texts.isEmpty()) {
//			for(int i=0;i<texts.size();i++) {
//				UnicodeText ut = texts.get(i);
//				SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
//				try {
//					json.get().put("type", "do_text");
//					json.get().put("kor", ut.isKor);
//					json.get().put("text", ut.text);
//					add(json);
//				} catch(Exception e) {}
//			}
//		}
//	}
	
	public void addDoWake() {
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", "do_key");
			json.get().put("event", "press");
			json.get().put("keycode", 224);
			addAgent(json);
		} catch(Exception e) {}
//		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
//		try {
//			json.get().put("type", "do_wake");
//			add(json);
//		} catch(Exception e) {}
	}
	
    public static final String SELECTOR_NATIVE_ID = "id";
    public static final String SELECTOR_XPATH = "xpath";
    public static final String SELECTOR_ACCESSIBILITY_ID = "accessibility id";
    public static final String SELECTOR_CLASS = "class name";
    public static final String SELECTOR_COORDINATE = "coordinate";
    public static final String SELECTOR_ANDROID_UIAUTOMATOR = "-android uiautomator";

    public void addFindXPath(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String xpath) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "find");
			jsonRef.get().put("strategy", SELECTOR_XPATH);
			jsonRef.get().put("selector", xpath);
			jsonRef.get().put("multiple", false);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addFindCoordinate(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, int x, int y) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "find");
			jsonRef.get().put("strategy", SELECTOR_COORDINATE);
			JSONObject jsonCoord = new JSONObject();
            jsonCoord.put("x", x);
            jsonCoord.put("y", y);
            jsonRef.get().put("selector", jsonCoord.toString());
			jsonRef.get().put("multiple", false);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addGetFocus(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "getFocus");
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addGetCache(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "getCache");
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClickFocus(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "click");
			jsonRef.get().put("focus", true);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClickCache(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "click");
			jsonRef.get().put("cache", true);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClickXPath(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String xpath) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "click");
			jsonRef.get().put(SELECTOR_XPATH, xpath);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClickCoordinate(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, int x, int y) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "click");
			JSONObject jsonCoord = new JSONObject();
            jsonCoord.put("x", x);
            jsonCoord.put("y", y);
            jsonRef.get().put(SELECTOR_COORDINATE, jsonCoord.toString());
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addGetTextFocus(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "getText");
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addGetTextCache(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("cache", true);
			jsonRef.get().put("type", "getText");
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addGetTextXPath(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String xpath) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "getText");
			jsonRef.get().put(SELECTOR_XPATH, xpath);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addGetTextCoordinate(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, int x, int y) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "getText");
			JSONObject jsonCoord = new JSONObject();
            jsonCoord.put("x", x);
            jsonCoord.put("y", y);
            jsonRef.get().put(SELECTOR_COORDINATE, jsonCoord.toString());
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addSetTextFocus(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String text, boolean clear) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "setText");
			jsonRef.get().put("text", text);
			jsonRef.get().put("unicode", true); // default: true
            jsonRef.get().put("clear", true); // default: true
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addSetTextCache(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String text, boolean clear) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "setText");
			jsonRef.get().put("text", text);
			jsonRef.get().put("cache", true);
			jsonRef.get().put("unicode", true); // default: true
            jsonRef.get().put("clear", clear); // default: true
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addSetTextXPath(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String xpath, String text, boolean clear) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "setText");
			jsonRef.get().put(SELECTOR_XPATH, xpath);
			jsonRef.get().put("text", text);
			jsonRef.get().put("unicode", true); // default: true
            jsonRef.get().put("clear", clear); // default: true
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addSetTextCoordinate(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, int x, int y, String text, boolean clear) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "setText");
			JSONObject jsonCoord = new JSONObject();
            jsonCoord.put("x", x);
            jsonCoord.put("y", y);
            jsonRef.get().put(SELECTOR_COORDINATE, jsonCoord.toString());
			jsonRef.get().put("text", text);
			jsonRef.get().put("unicode", true); // default: true
            jsonRef.get().put("clear", clear); // default: true
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClearTextFocus(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "clearText");
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClearTextCache(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "clearText");
			jsonRef.get().put("cache", true);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClearTextXPath(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, String xpath) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "clearText");
			jsonRef.get().put(SELECTOR_XPATH, xpath);
			addService(jsonRef);
		} catch(Exception e) {}
    }
	
    public void addClearTextCoordinate(ScriptRunner runner, RemoteMessageWriter writer, JSONObject jsonReq, int x, int y) {
    	SoftReference<JSONObject> jsonRef = new SoftReference<JSONObject>(new JSONObject());
    	String uuid = UUID.randomUUID().toString();
		try {
			mServiceCmdMap.put(uuid, new ServiceCommander(runner, writer, jsonReq));
			jsonRef.get().put("uuid", uuid);
			jsonRef.get().put("type", "clearText");
			JSONObject jsonCoord = new JSONObject();
            jsonCoord.put("x", x);
            jsonCoord.put("y", y);
            jsonRef.get().put(SELECTOR_COORDINATE, jsonCoord.toString());
			addService(jsonRef);
		} catch(Exception e) {}
    }

	private int toAndroidKeyCode(int keyCode) {
		int keyCodeAndroid = -1;
		
		if(keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
			keyCodeAndroid = (keyCode-KeyEvent.VK_0) + 7;
		else if(keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)
			keyCodeAndroid = (keyCode-KeyEvent.VK_A) + 29;
		else if(keyCode == KeyEvent.VK_F11)
			keyCodeAndroid = 5;
		else if(keyCode == KeyEvent.VK_F12)
			keyCodeAndroid = 6;
		else if(keyCode == KeyEvent.VK_ASTERISK)
			keyCodeAndroid = 17;
		else if(keyCode == KeyEvent.VK_NUMBER_SIGN)
			keyCodeAndroid = 18;
		else if(keyCode == KeyEvent.VK_UP)
			keyCodeAndroid = 19;
		else if(keyCode == KeyEvent.VK_DOWN)
			keyCodeAndroid = 20;
		else if(keyCode == KeyEvent.VK_LEFT)
			keyCodeAndroid = 21;
		else if(keyCode == KeyEvent.VK_RIGHT)
			keyCodeAndroid = 22;
		else if(keyCode == KeyEvent.VK_PRINTSCREEN)
			keyCodeAndroid = 27;
		else if(keyCode == KeyEvent.VK_COMMA)
			keyCodeAndroid = 55;
		else if(keyCode == KeyEvent.VK_PERIOD)
			keyCodeAndroid = 56;
		else if(keyCode == KeyEvent.VK_SHIFT)
			keyCodeAndroid = 59;
		else if(keyCode == KeyEvent.VK_TAB)
			keyCodeAndroid = 61;
		else if(keyCode == KeyEvent.VK_SPACE)
			keyCodeAndroid = 62;
		else if(keyCode == KeyEvent.VK_ENTER || keyCode == 13)
			keyCodeAndroid = 66;
		else if(keyCode == KeyEvent.VK_BACK_SPACE)
			keyCodeAndroid = 67;
		else if(keyCode == KeyEvent.VK_DELETE)
			keyCodeAndroid = 112;
		else if(keyCode == KeyEvent.VK_BACK_QUOTE)
			keyCodeAndroid = 68;
		else if(keyCode == KeyEvent.VK_MINUS)
			keyCodeAndroid = 69;
		else if(keyCode == KeyEvent.VK_EQUALS)
			keyCodeAndroid = 70;
		else if(keyCode == KeyEvent.VK_OPEN_BRACKET)
			keyCodeAndroid = 71;
		else if(keyCode == KeyEvent.VK_CLOSE_BRACKET)
			keyCodeAndroid = 72;
		else if(keyCode == KeyEvent.VK_BACK_SLASH)
			keyCodeAndroid = 73;
		else if(keyCode == KeyEvent.VK_SEMICOLON)
			keyCodeAndroid = 74;
		else if(keyCode == KeyEvent.VK_QUOTE)
			keyCodeAndroid = 75;
		else if(keyCode == KeyEvent.VK_SLASH)
			keyCodeAndroid = 76;
		else if(keyCode == KeyEvent.VK_AT)
			keyCodeAndroid = 77;
		else if(keyCode == KeyEvent.VK_PLUS)
			keyCodeAndroid = 81;
		else if(keyCode == KeyEvent.VK_PAGE_UP)
			keyCodeAndroid = 92;
		else if(keyCode == KeyEvent.VK_PAGE_DOWN)
			keyCodeAndroid = 93;
		else if(keyCode == KeyEvent.VK_ESCAPE)
			keyCodeAndroid = 111;
		else if(keyCode == KeyEvent.VK_CAPS_LOCK)
			keyCodeAndroid = 115;
		else if(keyCode == KeyEvent.VK_SCROLL_LOCK)
			keyCodeAndroid = 116;
		else if(keyCode == KeyEvent.VK_PAUSE)
			keyCodeAndroid = 121;
		else if(keyCode == KeyEvent.VK_HOME)
			keyCodeAndroid = 122;
		else if(keyCode == KeyEvent.VK_END)
			keyCodeAndroid = 123;
		else if(keyCode == KeyEvent.VK_INSERT)
			keyCodeAndroid = 124;
		else if(keyCode == KeyEvent.VK_NUM_LOCK)
			keyCodeAndroid = 143;
		else if(keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12)
			keyCodeAndroid = (keyCode-KeyEvent.VK_F1) + 131;
		else if(keyCode == 0xAD) // VK_VOLUME_MUTE
			keyCodeAndroid = 91;
		else if(keyCode == 0xAE) // VK_VOLUME_UP
			keyCodeAndroid = 25;
		else if(keyCode == 0xAF) // VK_VOLUME_DOWN
			keyCodeAndroid = 24;
		else if(keyCode == 0xB0) // VK_MEDIA_NEXT_TRACK
			keyCodeAndroid = 87;
		else if(keyCode == 0xB1) // VK_MEDIA_PREV_TRACK
			keyCodeAndroid = 88;
		else if(keyCode == 0xB2) // VK_MEDIA_STOP
			keyCodeAndroid = 86;
		else if(keyCode == 0xB3) // VK_MEDIA_PLAY_PAUSE
			keyCodeAndroid = 85;
		else if(keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9)
			keyCodeAndroid = (keyCode-KeyEvent.VK_NUMPAD0) + 7;
		else if(keyCode == KeyEvent.VK_DIVIDE)
			keyCodeAndroid = 154;
		else if(keyCode == KeyEvent.VK_MULTIPLY)
			keyCodeAndroid = 155;
		else if(keyCode == KeyEvent.VK_SUBTRACT)
			keyCodeAndroid = 156;
		else if(keyCode == KeyEvent.VK_ADD)
			keyCodeAndroid = 157;
		else if(keyCode == KeyEvent.VK_DECIMAL)
			keyCodeAndroid = 56;
		else if(keyCode == KeyEvent.VK_SEPARATOR)
			keyCodeAndroid = 159;
		else if(keyCode == KeyEvent.VK_KANA) // 한/영
			keyCodeAndroid = 218;
		else if(keyCode == KeyEvent.VK_KANJI) // 한자
			keyCodeAndroid = 212;
		else if(keyCode == KeyEvent.VK_KP_UP)
			keyCodeAndroid = 19;
		else if(keyCode == KeyEvent.VK_KP_DOWN)
			keyCodeAndroid = 20;
		else if(keyCode == KeyEvent.VK_KP_LEFT)
			keyCodeAndroid = 21;
		else if(keyCode == KeyEvent.VK_KP_RIGHT)
			keyCodeAndroid = 22;
    
		return keyCodeAndroid;
	}
	
	private String decodeUTF8(byte[] bytes) {
	    return new String(bytes, UTF8_CHARSET);
	}

	private byte[] encodeUTF8(String string) {
	    return string.getBytes(UTF8_CHARSET);
	}
	
	class ServiceCommander {
		public ScriptRunner runner = null;
		public RemoteMessageWriter writer = null;
		public JSONObject jsonReq = null;
		
		public ServiceCommander(ScriptRunner runner, RemoteMessageWriter writer, JSONObject json) {
			this.runner = runner;
			this.writer = writer;
			this.jsonReq = json;
		}
	}
	
//	class ClientKeyboard implements Runnable {
//		private int retryConn = 0;
//		private boolean isRun = true;
//		
//		@Override
//		public void run() {
//			Thread thread = Thread.currentThread();
//			
//			while(isRunning && isRun && mEngine.isRunning() && !thread.isInterrupted()) {
//				Socket socket = null;
//				BufferedOutputStream mStreamOutput = null;
//				try {
//					socket = new Socket("localhost", mEngine.KBD_PORT);
//					mStreamOutput = new BufferedOutputStream(socket.getOutputStream());
//					
//					while(isRunning && isRun && mEngine.isRunning() && !thread.isInterrupted()) {
//						if(mKbdCmdQueue.isEmpty()) {
//							Thread.sleep(1);
//							continue;
//						}
//						
//						SoftReference<JSONObject> obj = mKbdCmdQueue.poll();
//						String sCmd = obj.get().toString();
//						AnsiLog.d("Kbd cmd : " + sCmd);
//						byte[] cmd = encodeUTF8(sCmd + "\n");
//						mStreamOutput.write(cmd);
//						mStreamOutput.flush();
//						obj.clear();
//						
//						try { Thread.sleep(1); } catch (InterruptedException e) {}
//					}
//				} catch(Exception e) {
//					//e.printStackTrace();
//					if(isRunning && isRun && mEngine.isRunning()) {
//						retryConn++;
//						if(retryConn >= MirrorEngine.MAX_RETRY_CONNECT) {
//							isRun = false;
//						}
//					}
//				} finally {
//					if (mStreamOutput != null) {
//						try {
//							mStreamOutput.close();
//							mStreamOutput = null;
//						} catch (IOException e) {}
//					}
//					if (socket != null && socket.isConnected()) {
//						try {
//							socket.close();
//						} catch (IOException e) {}
//					}
//				}
//				
//				if(isRunning && isRun && mEngine.isRunning() &&!thread.isInterrupted())
//					try { Thread.sleep(1000); } catch (InterruptedException e) {}
//			}
//		}
//	}
}
