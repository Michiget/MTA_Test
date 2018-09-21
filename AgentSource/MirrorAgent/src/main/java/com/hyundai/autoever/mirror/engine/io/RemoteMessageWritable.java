package com.hyundai.autoever.mirror.engine.io;

import org.json.JSONObject;

public interface RemoteMessageWritable {
	public void write(final JSONObject message);
}
