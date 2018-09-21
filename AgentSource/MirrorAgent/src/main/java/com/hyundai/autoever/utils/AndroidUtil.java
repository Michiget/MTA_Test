package com.hyundai.autoever.utils;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

public class AndroidUtil {
	
	public static String getApkPackageName(String apk) {
		String pkg = null;
		String output;
		String localPath = System.getProperty("user.dir") + File.separator + "platform-tools";
		String aapt = localPath + File.separator + "aapt dump badging ";
		String cmd = aapt + apk;
		
		output = execCmd(cmd).trim();
		if(!output.isEmpty()) {
			Pattern p = Pattern.compile("package: name\\='(.+)' versionC");
			Matcher m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					pkg = m.group(1);
				}
			}
		}
		return pkg;
	}
	
	public static JSONObject getApkInfo(String apk) {
		JSONObject json = null;
		String output;
		String localPath = System.getProperty("user.dir") + File.separator + "platform-tools";
		String aapt = localPath + File.separator + "aapt dump badging ";
		String cmd = aapt + apk;
		
		output = execCmd(cmd).trim();
		if(!output.isEmpty()) {
			Pattern p = Pattern.compile("package: name\\='(.+)' versionC");
			Matcher m = p.matcher(output);
			if(m.find()) {
				json = new JSONObject();
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					try { json.put("package", m.group(1)); } catch(JSONException e) {}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("versionCode\\='(.+)' version");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("versionCode", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("versionName\\='(.+)' platform");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("versionName", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("sdkVersion:'(.+)'");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("sdkVersion", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("targetSdkVersion:'(.+)'");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("targetSdkVersion", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
		}
		return json;
	}
	
	public static JSONObject getPkgInfo(IDevice dev, String pkg) {
		JSONObject json = null;
		String output;
		String cmd = "dumpsys package " + pkg;
		
		output = executeShellCommand(dev, cmd).trim();
		if(!output.isEmpty()) {
			Pattern p;
			Matcher m;
			
			p = Pattern.compile("versionCode\\=([0-9]+) target");
			m = p.matcher(output);
			if(m.find()) {
				json = new JSONObject();
				try { json.put("package", pkg); } catch(JSONException e) {}
				
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					try { json.put("versionCode", m.group(1)); } catch(JSONException e) {}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("versionName\\=(.+)");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("versionName", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
			
			if(json != null) {
				p = Pattern.compile(" targetSdk\\=([0-9]+)");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("targetSdkVersion", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("firstInstallTime\\=(.+)");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("firstInstallTime", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
			
			if(json != null) {
				p = Pattern.compile("lastUpdateTime\\=(.+)");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 1) { // groupCount bug???(+1)
						try { json.put("lastUpdateTime", m.group(1)); } catch(JSONException e) {}
					}
				}
			}
		}
		return json;
	}
	
	public static JSONObject getTopActivity(IDevice dev) {
		JSONObject json = null;
		String output;
		String cmd = "dumpsys activity top";
		
		output = executeShellCommand(dev, cmd).trim();
		if(!output.isEmpty()) {
			String pkg = null, activity = null;
			Pattern p;
			Matcher m;
			
			p = Pattern.compile("TASK (.+) id");
			m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					pkg = m.group(1);
				}
			}
			
			if(pkg != null) {
				p = Pattern.compile("ACTIVITY (.+)/(.+) [a-zA-Z0-9]+ pid");
				m = p.matcher(output);
				if(m.find()) {
					if(m.groupCount() == 2) { // groupCount bug???(+1)
						activity = m.group(2);
					}
				}
				
				json = getPkgInfo(dev, pkg);
				if(json != null) {
					try { 
						json.put("package", pkg); 
						if(activity != null)
							json.put("activity", activity);
					} catch(JSONException e) {}
				}
			}
		}
		return json;
	}

	public static String execCmd(String cmd) {
	    String val = "";
		try {
		    Process proc = Runtime.getRuntime().exec(cmd);
		    java.io.InputStream is = proc.getInputStream();
		    java.io.BufferedReader stdis = new java.io.BufferedReader(new java.io.InputStreamReader(is));
		    StringBuffer stdout = new StringBuffer();
		    String s = null;
		    while ((s = stdis.readLine()) != null && !s.isEmpty()) {
		        stdout.append(s);
		        stdout.append("\n");
		    }
		    val = stdout.toString();
		}
		catch(java.io.IOException e) {e.printStackTrace();}
	    return val;
	}
	
	public static String executeShellCommand(IDevice dev, String command, CollectingOutputReceiver outputReceiver) {
		try {
			dev.executeShellCommand(command, outputReceiver, 0);
		} catch (TimeoutException e) {
			//e.printStackTrace();
		} catch (AdbCommandRejectedException e) {
			//e.printStackTrace();
		} catch (ShellCommandUnresponsiveException e) {
			//e.printStackTrace();
		} catch (IOException e) {
			//e.printStackTrace();
		}
		return outputReceiver.getOutput();
	}
	
	public static String executeShellCommand(IDevice dev, String command) {
		if(dev == null)
			return "";
		
		CollectingOutputReceiver output = new CollectingOutputReceiver();
		return executeShellCommand(dev, command, output);
	}
}
