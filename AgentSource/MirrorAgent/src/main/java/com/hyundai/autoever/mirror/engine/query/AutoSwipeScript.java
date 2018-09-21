package com.hyundai.autoever.mirror.engine.query;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.utils.ImageUtil;

public class AutoSwipeScript extends AbstractRemoteCommand {
	public static int DEFAULT_REPEAT = 50;
	public static int DEFAULT_LIMIT_TIME = 5*60000;
	public static int DEFAULT_WAIT_TIME = 2000;
	public static double DEFAULT_SIMILARITY = 0.9;

	public AutoSwipeScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}
	
	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);
		
		if(method.compareToIgnoreCase("autocancel") == 0) {
			actioncancel(ACT_TYPE_AUTOSWIPE);
		}
		else {
			int repeat = DEFAULT_REPEAT;
			int limit = DEFAULT_LIMIT_TIME;
			int wait = DEFAULT_WAIT_TIME;
			double similarity = DEFAULT_SIMILARITY;
			if(args.has("repeat")) {
				try { repeat = args.getInt("repeat"); } catch(JSONException e) {}
				if(repeat < 2) repeat = 2;
			}
			if(args.has("limit")) {
				try { limit = args.getInt("limit"); } catch(JSONException e) {}
				if(limit < 10000) limit = 10000;
			}
			if(args.has("wait")) {
				try { wait = args.getInt("wait"); } catch(JSONException e) {}
				if(wait < DEFAULT_WAIT_TIME) wait = DEFAULT_WAIT_TIME;
			}
			if(args.has("similarity")) {
				try { similarity = args.getInt("similarity"); } catch(JSONException e) {}
				if(similarity < 0.5) similarity = 0.5;
			}
			if(args.has("startx") && args.has("starty") && args.has("destx") && args.has("desty") && args.has("query_image")) {
				SoftReference<BufferedImage> queryImage = ImageUtil.decodeByBase64Ref(args.getString("query_image"));
				if(queryImage.get() != null) {
					if(!args.has("repeat"))
						args.put("repeat", repeat);
					if(!args.has("limit"))
						args.put("limit", limit);
					if(!args.has("wait"))
						args.put("wait", wait);
					if(!args.has("similarity"))
						args.put("similarity", similarity);
					
					action(queryImage);
				}
				else
					log.error(9111, "[method:" + method + "] " + "Base64 text is NOT an image.");
			}
			else 
				log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		}
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}

}
