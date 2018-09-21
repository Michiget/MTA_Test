package com.hyundai.autoever.mirror.engine.query;

import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.ServerModule;
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

public class ShellCommand extends AbstractRemoteCommand {
	public ShellCommand(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);

		if(ServerModule.get().getUrlSetLog() != null) {
			if(args.has("key") && args.has("shell")) {
				action();
			}
			else 
				log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		}
		else
			log.error(9103, "[method:" + method + "] " + "not supported method : " + args.toString());
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}

}
