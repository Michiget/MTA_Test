package com.hyundai.autoever.mirror.engine.query;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.ScriptRunner;
import com.hyundai.autoever.utils.AnsiLog;

public abstract class AbstractRemoteCommand {
	protected CommandServer.ConnectionCommandWrap wrap = null;
	protected AnsiLog log = null;
	protected String method = "";
	protected JSONObject jsonData = null;
	protected boolean withMain = true;
	protected boolean onlyMain = false;
	protected ExecutorService mExecutorScript = null;
	
	protected final static int 
		ACT_TYPE_SWIPE = 1,
		ACT_TYPE_FIND = 2,
		ACT_TYPE_AUTOSWIPE = 3;
	
	public AbstractRemoteCommand(CommandServer.ConnectionCommandWrap wrap) {
		this.wrap = wrap;
		this.log = new AnsiLog();
		this.mExecutorScript = Executors.newCachedThreadPool();
		this.method = "";
		this.jsonData = null;
		this.withMain = true;
		this.onlyMain = false;
	}
	public abstract JSONObject respond(JSONObject args) throws IOException, JSONException;
	
	public void cleanup() {
		stopScript();
	}
	
	protected void init(JSONObject args) throws JSONException {
		log.clearError();
		jsonData = args;
		method = args.getString("method");
		withMain = true;
		onlyMain = false;
		if(args.has("with_main"))
			withMain = args.getBoolean("with_main");
		if(args.has("only_main"))
			onlyMain = args.getBoolean("only_main");
	}
	
	protected void setOnlyMain(boolean only_main) {
		onlyMain = only_main;
		try {
			if(jsonData != null && (!jsonData.has("only_main") || jsonData.getBoolean("only_main") != onlyMain))
				jsonData.put("only_main", onlyMain);
		} catch(JSONException e) {}
	}
	
	protected void setWithMain(boolean with_main) {
		withMain = with_main;
		try {
			if(jsonData != null && (!jsonData.has("with_main") || jsonData.getBoolean("with_main") != withMain))
				jsonData.put("with_main", withMain);
		} catch(JSONException e) {}
	}
	
	protected boolean startScript(MirrorEngine engine, CommandServer.ConnectionCommandWrap wrap, SoftReference<BufferedImage> queryImage) {
		setWithMain(withMain);
		ScriptRunner detector = engine.createScriptRunner(wrap, queryImage, jsonData);
		mExecutorScript.submit(detector);
		return true;
	}
	
	protected boolean startScript(MirrorEngine engine, CommandServer.ConnectionCommandWrap wrap) {
		setWithMain(withMain);
		ScriptRunner detector = engine.createScriptRunner(wrap, jsonData);
		mExecutorScript.submit(detector);
		return true;
	}
	
	protected void stopScript() {
		try {
			mExecutorScript.shutdown();
    	    mExecutorScript.awaitTermination(2000, TimeUnit.MILLISECONDS);
    	}
    	catch (InterruptedException e) {
    	}
    	finally {
    	    if (!mExecutorScript.isTerminated()) {
    	    	mExecutorScript.shutdownNow();
    	    }
    	}
	}
	
	protected boolean actionBase() {
		MirrorEngine baseEngine = wrap.getBaseEngine();
		if(baseEngine == null)
			return false;
		
		startScript(baseEngine, wrap);
		return true;
	}
	
	protected boolean action() {
		if(onlyMain)
			return actionBase();
		
		ConcurrentMap<String, MirrorEngine> engines = wrap.getEngines();
		for(String serial: engines.keySet())
			startScript(engines.get(serial), wrap);
		return true;
	}
	
	protected void action(SoftReference<BufferedImage> queryImage) {
		MirrorEngine baseEngine = wrap.getBaseEngine();
		
		if((onlyMain || withMain) && baseEngine != null) {
			startScript(baseEngine, wrap, queryImage);
		}
		
		if(!onlyMain) {
			ConcurrentMap<String, MirrorEngine> engines = wrap.getEngines();
			for(String serial: engines.keySet()) {
				MirrorEngine engine = engines.get(serial);
				if(withMain && baseEngine != null && baseEngine == engine)
					continue;
				
				startScript(engine, wrap, queryImage);
			}
		}
	}
	
	protected void actioncancel(int type) {
		if(onlyMain) {
			MirrorEngine baseEngine = wrap.getBaseEngine();
			switch(type){
			case ACT_TYPE_SWIPE:
				baseEngine.setCancelSwipe(true);
				break;
			case ACT_TYPE_FIND:
				baseEngine.setCancelFind(true);
				break;
			case ACT_TYPE_AUTOSWIPE:
				baseEngine.setCancelAutoSwipe(true);
				break;
			}
		}
		else {
			ConcurrentMap<String, MirrorEngine> engines = wrap.getEngines();
			for(String serial: engines.keySet()) {
				switch(type){
				case ACT_TYPE_SWIPE:
					engines.get(serial).setCancelSwipe(true);
					break;
				case ACT_TYPE_FIND:
					engines.get(serial).setCancelFind(true);
					break;
				case ACT_TYPE_AUTOSWIPE:
					engines.get(serial).setCancelAutoSwipe(true);
					break;
				}
			}
		}
	}
	
	public static JSONObject success(String method, JSONObject data) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", true);
			res.put("result_code", 0);
			res.put("result_msg", "success");
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}
	
	public static JSONObject success(String method, String serial, JSONObject data) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", true);
			res.put("result_code", 0);
			res.put("result_msg", "success");
			if(serial != null && !serial.isEmpty())
				res.put("serial", serial);
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}
	
	public static JSONObject success(String method, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", true);
			res.put("result_code", code);
			res.put("result_msg", msg);
			
			JSONObject data = new JSONObject();
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}
	
	public static JSONObject success(String method, JSONObject data, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", true);
			res.put("result_code", code);
			res.put("result_msg", msg);
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}
	
	public static JSONObject success(String method, String serial, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", true);
			res.put("result_code", code);
			res.put("result_msg", msg);
			if(serial != null && !serial.isEmpty())
				res.put("serial", serial);
			
			JSONObject data = new JSONObject();
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}
	
	public static JSONObject success(String method, String serial, JSONObject data, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", true);
			res.put("result_code", code);
			res.put("result_msg", msg);
			if(serial != null && !serial.isEmpty())
				res.put("serial", serial);
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}

	public static JSONObject failed(String method, JSONObject data) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", false);
			res.put("result_code", 1);
			res.put("result_msg", "fail");
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}

	public static JSONObject failed(String method, String serial, JSONObject data) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", false);
			res.put("result_code", 1);
			res.put("result_msg", "fail");
			if(serial != null && !serial.isEmpty())
				res.put("serial", serial);
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}

	public static JSONObject failed(String method, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", false);
			res.put("result_code", code);
			res.put("result_msg", msg);
			
			JSONObject data = new JSONObject();
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}

	public static JSONObject failed(String method, JSONObject data, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", false);
			res.put("result_code", code);
			res.put("result_msg", msg);
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}

	public static JSONObject failed(String method, String serial, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", false);
			res.put("result_code", code);
			res.put("result_msg", msg);
			if(serial != null && !serial.isEmpty())
				res.put("serial", serial);
			
			JSONObject data = new JSONObject();
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}

	public static JSONObject failed(String method, String serial, JSONObject data, String msg, int code) {
		JSONObject res = new JSONObject();
		try {
			res.put("method", method);
			res.put("success", false);
			res.put("result_code", code);
			res.put("result_msg", msg);
			if(serial != null && !serial.isEmpty())
				res.put("serial", serial);
			
			data.put("method", method);
			res.put("data", data);
		} catch(JSONException e) {}
		return res;
	}
}
