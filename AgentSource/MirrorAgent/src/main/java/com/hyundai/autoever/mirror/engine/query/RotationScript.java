package com.hyundai.autoever.mirror.engine.query;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;

public class RotationScript extends AbstractRemoteCommand {
	public RotationScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);

		if(args.has("rotation") || args.has("orientation")) {
			action();
		}
		else 
			log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}

}
