package com.hyundai.autoever.mirror.engine.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

public class RemoteMessageReader {
	private InputStream in;

    public RemoteMessageReader(InputStream in) {
        this.in = in;
    }

    public JSONObject read() throws IOException, JSONException {
    	JSONObject json = null;
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

        String inputStr = streamReader.readLine();
        if(inputStr != null && inputStr.length() > 0)
            json = new JSONObject(inputStr);

        return json;
    }
}
