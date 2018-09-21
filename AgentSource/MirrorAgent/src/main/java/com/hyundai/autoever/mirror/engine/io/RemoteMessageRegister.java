package com.hyundai.autoever.mirror.engine.io;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.query.*;

public class RemoteMessageRegister {
	private CommandServer.ConnectionCommandWrap wrap;
	private RemoteMessageRouter router = null;
	
	public RemoteMessageRegister(CommandServer.ConnectionCommandWrap wrap, RemoteMessageWriter writer) {
		this.wrap = wrap;
		this.router = new RemoteMessageRouter(writer);
		init();
	}
	
	private void init() {
		TouchScript ts = new TouchScript(wrap);
		GestureScript gs = new GestureScript(wrap);
		AutoSwipeScript as = new AutoSwipeScript(wrap);
		FindScript fs = new FindScript(wrap);
		CropScript cc = new CropScript(wrap);
		
		router.register("tap", ts);
		router.register("click", ts);
		router.register("doubletap", ts);
		router.register("doubleclick", ts);
		router.register("hold", ts);
		router.register("longclick", ts);
		router.register("touchdown", ts);
		router.register("touchmove", ts);
		router.register("touchup", ts);
		
		router.register("swipe", gs);
		router.register("drag", gs);
		router.register("swipecancel", gs);
		router.register("dragcancel", gs);
		
		router.register("autotap", as);
		router.register("autofind", as);
		router.register("autocancel", as);
		
		router.register("find", fs);
		router.register("findcancel", fs);
		
		router.register("crop", cc);
		router.register("shot", cc);
		
		router.register("init", new InitCommand(wrap));
		router.register("base_device", new BaseDeviceScript(wrap));
		router.register("rotation", new RotationScript(wrap));
		router.register("key", new SendKeyScript(wrap));
		router.register("text", new TextScript(wrap));
		router.register("monitor", new MonitorCommand(wrap));
		router.register("app", new AppScript(wrap));
		router.register("shell", new ShellCommand(wrap));
	}
	
	public RemoteMessageRouter getRouter() {
		return router;
	}
}
