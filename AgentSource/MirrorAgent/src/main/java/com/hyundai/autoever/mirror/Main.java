package com.hyundai.autoever.mirror;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.bridj.util.ProcessUtils;

import com.github.axet.desktop.os.win.handle.HANDLER_ROUTINE;
import com.github.axet.desktop.os.win.libs.Kernel32Ex;
import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorServer;
import com.hyundai.autoever.mirror.engine.ServerModule;
import com.hyundai.autoever.utils.AnsiLog;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public class Main {
	private static ServerModule mModule = null;
	private static MirrorServer mMirrorServer = null;
	private static CommandServer mCommandServer = null;
	private static ExecutorService mMainThreadPool = null;
	
	private static HANDLER_ROUTINE handler = new HANDLER_ROUTINE() {
		@Override
		public long callback(long dwCtrlType) {
			if ((int) dwCtrlType == 0 || (int) dwCtrlType == CTRL_CLOSE_EVENT || (int) dwCtrlType == CTRL_SHUTDOWN_EVENT) {
		    	if(mMirrorServer != null && mMirrorServer.isRunning()) {
		    		mMirrorServer.stop();
		    		mMirrorServer = null;
		    	}
		    	if(mCommandServer != null && mCommandServer.isRunning()) {
		    		mCommandServer.stop();
		    		mCommandServer = null;
		    	}
		    	if(mMainThreadPool != null) {
		    		try {
		    			mMainThreadPool.shutdown();
		    			mMainThreadPool.awaitTermination(2000, TimeUnit.MILLISECONDS);
			    	}
			    	catch (InterruptedException e) {
			    	}
			    	finally {
			    	    if (!mMainThreadPool.isTerminated()) {
			    	    	mMainThreadPool.shutdownNow();
			    	    }
			    	    mMainThreadPool = null;
			    	}
		    	}
		    	if(mModule != null) {
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
			String jsonFile = args[0];
			if(!mModule.setConfigJson(jsonFile))
				System.out.println("[USAGE] java -jar MirrorAgent.jar [agent.json].\nagent.json : agent json setting file.");
		}
		else {
			System.out.println("[USAGE] java -jar MirrorAgent.jar [agent.json].\nagent.json : agent json setting file.");
			finish = true;
		}
		
		if(!finish) {
			mModule.init();
			
			AnsiLog.i("Current Process PID : " + ProcessUtils.getCurrentProcessId());
			
			mMainThreadPool = Executors.newFixedThreadPool(20);
			mMirrorServer = new MirrorServer(mMainThreadPool);
			mCommandServer = new CommandServer(mMainThreadPool);
			
			Kernel32Ex.INSTANCE.SetConsoleCtrlHandler(handler, true);
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					mModule.loadDevices();
				}
			}).start();
			
			mMainThreadPool.submit(new Runnable() {
				@Override
				public void run() {
					mMirrorServer.start();
				}
			});
			mMainThreadPool.submit(new Runnable() {
				@Override
				public void run() {
					mCommandServer.start();
				}
			});
			
			mMainThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
		}
		mModule.uninit();
    }

}
