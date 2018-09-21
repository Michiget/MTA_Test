package com.hyundai.autoever.mirror.engine.query;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;

public class GestureScript extends AbstractRemoteCommand {
	private static int DEFAULT_DURATION = 5000;

	public GestureScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}
	
	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);
		
		if(method.compareToIgnoreCase("swipecancel") == 0 || method.compareToIgnoreCase("dragcancel") == 0) {
			actioncancel(ACT_TYPE_SWIPE);
		}
		else {
			int duration = DEFAULT_DURATION;
			if(args.has("duration")) {
				try { duration = args.getInt("duration"); } catch(JSONException e) {}
				if(duration > DEFAULT_DURATION) duration = DEFAULT_DURATION;
			}
			if(args.has("startx") && args.has("starty") && args.has("destx") && args.has("desty")) {
				if(!args.has("duration"))
					args.put("duration", duration);
				
				action();
			}
			else 
				log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		}
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}

}
