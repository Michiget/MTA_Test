package com.hyundai.autoever.mirror.engine.query;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;

public class CropScript extends AbstractRemoteCommand {
	public CropScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}
	
	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		boolean all = false;
		init(args);
		
		action();
		return null;
	}
}
