package com.hyundai.autoever.mirror.engine.query;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.ScriptRunner;
import com.hyundai.autoever.utils.AnsiLog;
import com.hyundai.autoever.utils.ImageUtil;

public class FindScript extends AbstractRemoteCommand {
	public static int DEFAULT_LIMIT_TIME = 5000;
	public static double DEFAULT_SIMILARITY = 0.7;
	
	public FindScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);
		
		if(method.compareToIgnoreCase("findcancel") == 0) {
			actioncancel(ACT_TYPE_FIND);
		}
		else if(args.has("element")) {
			action();
		}
		else if(args.has("query_image")) {
			int limit = DEFAULT_LIMIT_TIME;
			double similarity = DEFAULT_SIMILARITY;
			if(args.has("limit")) {
				try { limit = args.getInt("limit"); } catch(JSONException e) {}
				if(limit > DEFAULT_LIMIT_TIME) limit = DEFAULT_LIMIT_TIME;
			}
			if(args.has("similarity")) {
				try { similarity = args.getInt("similarity"); } catch(JSONException e) {}
				if(similarity < 0.5) similarity = 0.5;
			}
			String touchBase64 = args.getString("query_image");
			SoftReference<BufferedImage> queryImage = ImageUtil.decodeByBase64Ref(touchBase64);
			if(queryImage != null) {
				if(!args.has("limit"))
					args.put("limit", limit);
				if(!args.has("similarity"))
					args.put("similarity", similarity);
				
				action(queryImage);
			}
		}
		else 
			log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}
}
