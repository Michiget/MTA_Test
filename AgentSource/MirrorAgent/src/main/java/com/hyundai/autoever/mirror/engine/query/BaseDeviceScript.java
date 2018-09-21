package com.hyundai.autoever.mirror.engine.query;

import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.Window;
import com.hyundai.autoever.utils.AnsiLog;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;

public class BaseDeviceScript extends AbstractRemoteCommand {

	public BaseDeviceScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		log.clearError();
		method = args.getString("method");
		
		JSONObject jsonDev = args.getJSONObject("base_device");
		if(wrap.setBaseDevice(jsonDev))
			return success(args.getString("method"), args);
		
		log.error(9105,"[method:" + method + "] " +  "Invalid device.");
		return failed(method, args, log.getErrorMsg(), log.getErrorCode());
	}

}
