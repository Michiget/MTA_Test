package com.hyundai.autoever.mirror.engine.query;

import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.Window;
import com.hyundai.autoever.utils.AnsiLog;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;

public class MonitorCommand extends AbstractRemoteCommand {
	public MonitorCommand(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		String devSerial = null;
		String pkg = null;
		int tc = 0;
		boolean taz = false;
		
		if(args.has("serial"))
			devSerial = args.getString("serial");
		if(args.has("package"))
			pkg = args.getString("package");
		if(args.has("topCount"))
			tc = args.getInt("topCount");
		if(args.has("topAboveZero"))
			taz = args.getBoolean("topAboveZero");
		
		final String packageApp = pkg;
		final int topCount = tc;
		final boolean topAboveZero = taz;
		
		if(devSerial != null) {
			final MirrorEngine engine = wrap.findEngine(devSerial);
			if(engine != null) {
				mExecutorScript.submit(new Runnable() {
		            @Override
		            public void run() {
	                	JSONObject jsonObj = engine.getResourceStatus(packageApp, topCount, topAboveZero);
	            		wrap.getWriter().write(jsonObj);
		            }
		        });
			}
		}
		else {
			ConcurrentMap<String, MirrorEngine> engines = wrap.getEngines();
			for(String serial: engines.keySet()) {
				final MirrorEngine engine = engines.get(serial);
				mExecutorScript.submit(new Runnable() {
		            @Override
		            public void run() {
	                	JSONObject jsonObj = engine.getResourceStatus(packageApp, topCount, topAboveZero);
	            		wrap.getWriter().write(jsonObj);
		            }
		        });
			}
		}
		
		return null;
	}

}
