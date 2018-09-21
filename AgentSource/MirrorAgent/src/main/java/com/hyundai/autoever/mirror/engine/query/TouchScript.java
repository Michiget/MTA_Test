package com.hyundai.autoever.mirror.engine.query;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.imagerecognition.ImageLocation;
import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.ScriptRunner;
import com.hyundai.autoever.mirror.engine.Window;
import com.hyundai.autoever.utils.AnsiLog;
import com.hyundai.autoever.utils.ImageUtil;

public class TouchScript extends AbstractRemoteCommand {
	public static int DEFAULT_REGION_W = 3;
	public static int DEFAULT_REGION_H = 6;
	public static double DEFAULT_SIMILARITY = 0.7;

	public TouchScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}
	
	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);
		
		if(method.compareToIgnoreCase("touchup") == 0) {
			action();
		}
		else if(args.has("element")) {
			action();
		}		
		else {
			double regionw = DEFAULT_REGION_W;
			double regionh = DEFAULT_REGION_H;
			double similarity = DEFAULT_SIMILARITY;
			if(args.has("regionw")) {
				try { regionw = args.getDouble("regionw"); } catch(JSONException e) {}
			}
			if(args.has("regionh")) {
				try { regionh = args.getDouble("regionh"); } catch(JSONException e) {}
			}
			if(args.has("similarity")) {
				try { similarity = args.getInt("similarity"); } catch(JSONException e) {}
				if(similarity < 0.5) similarity = 0.5;
			}
			
			if(!args.has("regionw"))
				args.put("regionw", regionw);
			if(!args.has("regionh"))
				args.put("regionh", regionh);
			if(!args.has("similarity"))
				args.put("similarity", similarity);
			
			if(args.has("query_image")) {
				String touchBase64 = args.getString("query_image");
				SoftReference<BufferedImage> queryImage = ImageUtil.decodeByBase64Ref(touchBase64);
				if(queryImage != null) {
					action(queryImage);
				}
				else {
					log.error(9111, "[method:" + method + "] " + "Base64 text is NOT an image.");
				}
			}
			else if(args.has("cx") && args.has("cy")) {
				setWithMain(true);
				//setOnlyMain(true);
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
