package com.hyundai.autoever.device;

import java.io.File;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.hyundai.autoever.utils.Constant;


public class ADB {
	
	public static boolean hasInitAdb = false;
	
	private AndroidDebugBridge mAndroidDebugBridge = null;
	private String mAdbPath = null;
	private String mAdbPlatformTools = "platform-tools";
	
	public ADB(){
		init();
	}
	
	private String getADBPath(){
		if (mAdbPath == null){
			mAdbPath = System.getenv("ANDROID_SDK");
			if(mAdbPath != null){
				mAdbPath += File.separator + mAdbPlatformTools;
			}else {
				return null;
			}
		}
		mAdbPath += File.separator + "adb";
		return mAdbPath;
	}
	
	private String getMyADBPath(){
		if (mAdbPath == null){
			mAdbPath = Constant.getADBPlatformTools();
			mAdbPath += File.separator + "adb";
		}
		return mAdbPath;
	}
	
	public void init() {
		if(!hasInitAdb) {
			AndroidDebugBridge.init(false);
			hasInitAdb = true;
		}
		
		if(mAndroidDebugBridge == null) {
			String adbPath = getMyADBPath();
			mAndroidDebugBridge = AndroidDebugBridge.createBridge(adbPath, false);
		}
	}
	
	public boolean initDevices() {
		if(mAndroidDebugBridge == null)
			return false;
		
		boolean success = true;
		int loopCount = 0;
		
		success = true;
		while (mAndroidDebugBridge.hasInitialDeviceList() == false) {
			try {
				Thread.sleep(100);
				loopCount++;
			} catch (InterruptedException e) {
			}
			if (loopCount > 100) {
				success = false;
				break;
			}
		}
		
		return success;
	}
	
	public IDevice[] getDevices() {
		IDevice[] devicelist = null;
		if (mAndroidDebugBridge != null) {
			devicelist = mAndroidDebugBridge.getDevices();
		}
		return devicelist;
	}
}

