package com.hyundai.autoever.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.json.JSONException;
import org.json.JSONObject;

public class ConfigFile {
	private File mCfg = null;
	private JSONObject mJson = null;
	
	private static ConfigFile mShareCfg = null;
	
	public static ConfigFile get() {
		if(mShareCfg == null)
			mShareCfg = new ConfigFile(null);
		return mShareCfg;
	}
	
	public ConfigFile(String fileName) {
		if(fileName == null)
			fileName = "devices.json";
		String sDir = System.getenv("PROGRAMDATA") + File.separator + "MirrorAgent";
		String sFile = sDir + File.separator + fileName;
		File dir = new File(sDir);
		
		if(!dir.exists())
			dir.mkdirs();
		
		mCfg = new File(sFile);
		mJson = new JSONObject();
		
		File old = new File(System.getenv("APPDATA") + File.separator + "MirrorAgent" + File.separator + "share.json");
		if(old.exists())
			old.delete();
		delete();
		//load();
	}

	public JSONObject getObject() {
		return mJson;
	}
	
	public void reset() {
		mJson = new JSONObject();
	}
	
	public void save() {
		try {
			OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(mCfg), "UTF-8");
            BufferedWriter bufWriter = new BufferedWriter(writer);
            bufWriter.write(mJson.toString(2));
            bufWriter.close();
		} catch(IOException e) {
			e.printStackTrace();
		} catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	public void delete() {
		if(mCfg.exists())
			mCfg.delete();
	}
	
	public void load() {
		if(!mCfg.exists())
			return;
		
		try {
			InputStreamReader reader = new InputStreamReader(new FileInputStream(mCfg), "UTF-8");
			BufferedReader bufReader = new BufferedReader(reader);
			StringBuffer buffer = new StringBuffer();
			String line;
			while((line = bufReader.readLine()) != null) {
				buffer.append(line);
			}
			bufReader.close();
			mJson = new JSONObject(buffer.toString());
		} catch(IOException e) {
			e.printStackTrace();
		} catch(JSONException e) {
			e.printStackTrace();
		}
	}
}
