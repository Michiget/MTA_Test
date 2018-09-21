package com.hyundai.autoever.mirror.engine.query;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.utils.AnsiLog;

public class TextScript extends AbstractRemoteCommand {

	public TextScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);
		
		if(args.has("element") || args.has("text")) {
			action();
		}
		else 
			log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}

}
