package com.hyundai.autoever.mirror;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.ddmlib.IDevice;
import com.github.axet.desktop.os.win.handle.HANDLER_ROUTINE;
import com.github.axet.desktop.os.win.libs.Kernel32Ex;
import com.hyundai.autoever.device.ADB;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.mirror.engine.ServerModule;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public class Main {
	private static ServerModule mModule = null;
	
	private static HANDLER_ROUTINE handler = new HANDLER_ROUTINE() {
		@Override
		public long callback(long dwCtrlType) {
			if ((int) dwCtrlType == 0 || (int) dwCtrlType == CTRL_CLOSE_EVENT || (int) dwCtrlType == CTRL_SHUTDOWN_EVENT) {
				if(mModule != null) {
		    		mModule.closeDevices();
		    		mModule = null;
		    	}
				//return 1;
			}
			return 0;
		}
	};
	
	private interface CLibrary extends Library {
        CLibrary INSTANCE = (CLibrary)Native.loadLibrary((Platform.isWindows() ? "kernel32" : "c"), CLibrary.class);
        boolean SetConsoleTitleA(String title);
    }

	public static void main(String[] args) throws InterruptedException {
		boolean finish = false;
		mModule = ServerModule.get();
		
		CLibrary.INSTANCE.SetConsoleTitleA(ServerModule.TITLE);
		
		if(args.length > 0) {
			if(args[0].compareToIgnoreCase("-e") == 0) {
				MirrorEngine.encryptZip();
				finish = true;
			}
			else if(args[0].compareToIgnoreCase("-d") == 0) {
				MirrorEngine.decryptZip();
				finish = true;
			}
			else if(args[0].compareToIgnoreCase("-r") == 0) {
				mModule.init();
				mModule.removeMirrorOfAndroid();
				mModule.uninit();
				finish = true;
			}
//			else if(args[0].compareToIgnoreCase("-l") == 0) {
//				IDevice dev = getDevice("9fffb648");
//				if(dev != null)
//					runLogcat(dev, args.length > 1 ? args[1] : null);
//				finish = true;
//			}
//			else if(args[0].compareToIgnoreCase("-a") == 0) {
//				if(args.length > 1)
//					runGetApkInfo(args[1]);
//				finish = true;
//			}
			else {
				String jsonFile = args[0];
				if(!mModule.setConfigJson(jsonFile)) {
					System.out.println("[USAGE] java -jar DeviceAgent.jar [-e or -d or -r or agent.json].\n-e : make mirror.zip to mirror.dat.\n-d : make mirror.dat to mirrortest.zip.\n-r : remove the mirror module of android.\nagent.json : agent json setting file.");
					finish = true;
				}
			}
		}
		else {
			System.out.println("[USAGE] java -jar DeviceAgent.jar [-e or -d or -r or agent.json].\n-e : make mirror.zip to mirror.dat.\n-d : make mirror.dat to mirrortest.zip.\n-r : remove the mirror module of android.\nagent.json : agent json setting file.");
			finish = true;
		}
		
		if(!finish) {
			if(!mModule.initDevices()) {
				System.out.println("ADB가 정상적으로 동작하지 않거나 연결된 단말기가 없습니다.");
				return;
			}
			else {
				mModule.init();
				
				Kernel32Ex.INSTANCE.SetConsoleCtrlHandler(handler, true);
				
				mModule.loadDevices();
				mModule.waitDevices();
				mModule.uninit();
			}
		}
		else {
			System.exit(0);
		}
    }
	
	private static String execCmd(String cmd) {
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
	
	private static IDevice getDevice(String serial) {
		ADB adb = new ADB();
		if(!adb.initDevices())
			return null;
        
        if(adb.getDevices() == null || adb.getDevices().length == 0)
        	return null;
		
		int len = adb.getDevices().length;
		for(int i=0;i<len;i++) {
			IDevice dev = adb.getDevices()[i];
			if(dev.getSerialNumber().equalsIgnoreCase(serial)) {
				return dev;
			}
		}
		return null;
	}
	
	private static void runLogcat(IDevice dev, String pkg) {
		String output;
		String localPath = System.getProperty("user.dir") + File.separator + "platform-tools";
		String outfile = System.getProperty("user.dir") + File.separator + "logcat.txt";
		String cmd = "logcat -n 4 -v threadtime -d";
		if(pkg != null) {
			String ps = "ps | grep " + pkg;
			System.out.println(ps);
			output = MirrorEngine.executeShellCommand(dev, ps).trim();
		
			if(output.length() > 15) {
				cmd += " | grep -E \"\\.[0-9][0-9][0-9] " + output.substring(10,  15).trim() + "\"";
			}
		}
		
		System.out.println(cmd);
		output = MirrorEngine.executeShellCommand(dev, cmd).trim();
		try {
			java.io.PrintWriter out = new java.io.PrintWriter(outfile);
			out.print(output);
			out.close();
		}
		catch(java.io.IOException e) {e.printStackTrace();}
	}
	
	private static void runGetApkInfo(String apk) {
		String output, result = "";
		String localPath = System.getProperty("user.dir") + File.separator + "platform-tools";
		String outfile = System.getProperty("user.dir") + File.separator + "apkinfo.txt";
		String aapt = localPath + File.separator + "aapt dump badging ";
		String cmd = aapt + apk;
		
		System.out.println(cmd);
		output = execCmd(cmd).trim();
		if(!output.isEmpty()) {
			Pattern p = Pattern.compile("package: name\\='(.+)' versionC");
			Matcher m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					result = "package:" + m.group(1);
					result += "\n";
				}
			}
			p = Pattern.compile("versionCode\\='(.+)' version");
			m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					result += "versionCode:"+m.group(1);
					result += "\n";
				}
			}
			p = Pattern.compile("versionName\\='(.+)' platform");
			m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					result += "versionName:"+m.group(1);
					result += "\n";
				}
			}
			p = Pattern.compile("sdkVersion:'(.+)'");
			m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					result += "sdkVersion:"+m.group(1);
					result += "\n";
				}
			}
			p = Pattern.compile("targetSdkVersion:'(.+)'");
			m = p.matcher(output);
			if(m.find()) {
				if(m.groupCount() == 1) { // groupCount bug???(+1)
					result += "targetSdkVersion:"+m.group(1);
					result += "\n";
				}
			}
			
			if(!result.isEmpty())
				output = result;
		}
		try {
			java.io.PrintWriter out = new java.io.PrintWriter(outfile);
			out.print(output);
			out.close();
		}
		catch(java.io.IOException e) {e.printStackTrace();}
	}
}
