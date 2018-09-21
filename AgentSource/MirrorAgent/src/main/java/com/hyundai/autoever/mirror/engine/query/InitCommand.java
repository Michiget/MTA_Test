package com.hyundai.autoever.mirror.engine.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.ServerModule;
import com.hyundai.autoever.mirror.engine.Window;
import com.hyundai.autoever.utils.AnsiLog;

public class InitCommand extends AbstractRemoteCommand {
	private ServerModule mModule = null;

	public InitCommand(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
		
		mModule = ServerModule.get();
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		AnsiLog log = new AnsiLog();
		String method = args.getString("method");
		
		if(!args.has("base_device") || !args.has("devices"))
			log.error(9101, "Invalid Protocol : " + args.toString());
		else {
			JSONObject jsonDev = args.getJSONObject("base_device");
			if(!wrap.setBaseDevice(jsonDev))
				log.error(9102, "[method:" + method + "] " + "Invalid base device.");
			else {
				cleanup();
				
				JSONArray devices = args.getJSONArray("devices");
				for(int i=0;i<devices.length();i++) {
					JSONObject item = devices.getJSONObject(i);
					String serial = item.getString("serial");
					String model = item.getString("model");
					
					MirrorEngine engine = mModule.findEngine(serial);
					if(engine != null) {
						if(item.has("idx"))
							engine.setDeviceIdx(item.getInt("idx"));
						else
							engine.setDeviceIdx(-1);
						if(item.has("uxidx"))
							engine.setUxIdx(item.getInt("uxidx"));
						else
							engine.setUxIdx(-1);
//						if(item.has("enable"))
//							engine.setDeviceEnable(item.getBoolean("enable"));
//						if(item.has("visible"))
//							engine.setDeviceVisible(item.getBoolean("visible"));
						engine.initWindow();
						engine.registerObserver(wrap);
						
						if(!engine.isRunning()) {
							AnsiLog.d("[" + model + "] " + "The Mirror Device (" + serial + ") Starting - InitCommand.");
							engine.start();
						}
						
						wrap.addEngine(engine);
					}
				}
			}
		}
		
		if(wrap.getEngines().isEmpty())
			log.error(9104, "[method:" + method + "] " + "The Command Devices(EMPTY) of current Agent NOT exist.");
		else
			AnsiLog.i2(2002, "[method:" + method + "] " + "The Command Devices(" + wrap.getEngines().size() + " count) START.");
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return success(method, args);
	}
	
	@Override
	public void cleanup() {
		ConcurrentMap<String, MirrorEngine> engines = wrap.getEngines();
		for(String serial: engines.keySet()) {
			MirrorEngine engine = engines.get(serial);
			
			engine.removeObserver(wrap);
			if(engine.getObserverCount() == 0) {
				engine.stop();
			}
		}
		wrap.clearEngines();
		super.cleanup();
	}
}
