package com.hyundai.autoever.mirror.engine.io;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.query.*;
import com.hyundai.autoever.utils.AnsiLog;

public class RemoteMessageRouter {
    private Map<String, AbstractRemoteCommand> responders = new HashMap<String, AbstractRemoteCommand>();
    private RemoteMessageWriter writer;

    public RemoteMessageRouter(RemoteMessageWriter writer) {
        this.writer = writer;
    }

    public void register(String method, AbstractRemoteCommand responder) {
        responders.put(method, responder);
    }
    
    public boolean isRegister(String method) {
    	return responders.containsKey(method);
    }

    public boolean route(JSONObject json) throws IOException, JSONException {
    	AbstractRemoteCommand responder = responders.get(json.getString("method"));

        if (responder != null) {
            writer.write(responder.respond(json));
            return true;
        }
        return false;
    }

    public void cleanup() {
        for (AbstractRemoteCommand responder : responders.values()) {
            responder.cleanup();
        }
    }
}
