package com.hyundai.autoever.mirror.engine;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Runnable;
import java.lang.ref.SoftReference;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentMap;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.imagerecognition.ImageLocation;
import com.hyundai.autoever.mirror.engine.io.RemoteMessageWriter;
import com.hyundai.autoever.mirror.engine.query.AbstractRemoteCommand;
import com.hyundai.autoever.utils.AnsiLog;
import com.hyundai.autoever.utils.ImageUtil;

public class ScriptRunner implements Runnable {
	private MirrorEngine mEngine;
	private RemoteMessageWriter writer = null;
	private CommandServer.ConnectionCommandWrap wrap = null;
	private ClientService mClientService = null;
	private ResultService mResultService = null;
	private String method = "";
	private JSONObject json = null;
	private boolean mDoResult = false;
	private SoftReference<BufferedImage> queryImage = null;
	private boolean isMain = true;
	private String shotImageBase64 = null;
	private int rotation = 0;
	private boolean debug = false;
	
	public ScriptRunner(MirrorEngine engine, CommandServer.ConnectionCommandWrap wrap, JSONObject json) {
		this.mEngine = engine;
		this.wrap = wrap;
		this.json = json;
		
		reset();
	}
	
	public ScriptRunner(MirrorEngine engine, CommandServer.ConnectionCommandWrap wrap, SoftReference<BufferedImage> queryImage, JSONObject json) {
		this.mEngine = engine;
		this.wrap = wrap;
		this.queryImage = queryImage;
		this.json = json;
		
		reset();
	}
	
	private void reset() {
		this.mClientService = mEngine.getClientService();
		this.mResultService = mEngine.getResultService();

		this.writer = wrap.getWriter();
		
		try {
			if(json.has("method"))
				this.method = json.getString("method"); 
			
			if(json.has("only_main")) {
				isMain = json.getBoolean("only_main");
				if(!isMain && json.has("with_main"))
					isMain = json.getBoolean("with_main");
			}
			else if(json.has("with_main"))
				isMain = json.getBoolean("with_main");
		} catch(JSONException e) {}
		
		this.mDoResult = json.has("scriptidx");
		
		mEngine.setCancelSwipe(false);
		mEngine.setCancelAutoSwipe(false);
		mEngine.setCancelFind(false);
	}
	
	public boolean isBaseEngine() {
		MirrorEngine baseEngine = wrap.getBaseEngine();
		return baseEngine != null && baseEngine == mEngine;
	}
	
	public void addResult(int code, String msg, JSONObject scriptData, String shotImageBase64) {
		if(mEngine.getDeviceIdx() < 0 || !mDoResult)
			return;
		mResultService.add(mEngine.getDeviceIdx(), code, msg, scriptData, shotImageBase64, rotation);
	}
	
	public void addResult(int code, String msg, JSONObject scriptData) {
		if(mEngine.getDeviceIdx() < 0 || !mDoResult)
			return;
		//SoftReference<BufferedImage> shotImage = mEngine.getDetectImage();
		//shotImageBase64 = shotImage != null && shotImage.get() != null ? ImageUtil.encodeByBase64(shotImage.get()) : null;
		addResult(code, msg, scriptData, shotImageBase64);
	}
	
	public void addResultOK(JSONObject scriptData, String shotImageBase64) {
		if(mEngine.getDeviceIdx() < 0 || !mDoResult)
			return;
		addResult(0, "OK", scriptData, shotImageBase64);
	}
	
	public void addResultOK(JSONObject scriptData) {
		if(mEngine.getDeviceIdx() < 0 || !mDoResult)
			return;
		SoftReference<BufferedImage> shotImage = mEngine.getCloneDetectImage();
		shotImageBase64 = shotImage != null && shotImage.get() != null ? ImageUtil.encodeByBase64(shotImage.get()) : null;
		shotImage.clear();
		addResultOK(scriptData, shotImageBase64);
	}
	
	public void setLog(JSONObject scriptData, String output) {
		if(mEngine.getDeviceIdx() < 0)
			return;
		mResultService.setLog(mEngine.getDeviceIdx(), scriptData, output);
	}
	
	public static String execCmd(String cmd) throws java.io.IOException {
	    Process proc = Runtime.getRuntime().exec(cmd);
	    java.io.InputStream is = proc.getInputStream();
	    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
	    String val = "";
	    if (s.hasNext()) {
	        val = s.next();
	    }
	    else {
	        val = "";
	    }
	    s.close();
	    return val;
	}
	
	@Override
    public void run() {
		Thread thread = Thread.currentThread();
		try {
			if(!mEngine.isDeviceEnable())
				return;
			
			for(int i=0;i<30;i++) {
				if(!mEngine.isTouchDown() && 
					(method.compareToIgnoreCase("touchmove") == 0 || 
					method.compareToIgnoreCase("touchup") == 0)) {
					Thread.sleep(100);
				}
			}
			
			if(!mEngine.isTouchDown() && 
				(method.compareToIgnoreCase("touchmove") == 0 || 
				method.compareToIgnoreCase("touchup") == 0))
				return;
			
			AnsiLog log = new AnsiLog();
			ImageLocation imgLoc = null;
			
			rotation = mEngine.getWindow().getRotation();
			
			if(method.compareToIgnoreCase("touchup") == 0) {
				mEngine.doActionTouchEvent(wrap, method, -1, -1);
				if(isMain && isBaseEngine())
					writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
			}
			else if(method.compareToIgnoreCase("drag") == 0 || method.compareToIgnoreCase("swipe") == 0) {
				mEngine.doActionGestureEvent(wrap, method, json);
				if(!mEngine.isCancelSwipe && (mDoResult || (isMain && isBaseEngine())))
					writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
				addResultOK(json);
			}
			else if(method.compareToIgnoreCase("rotation") == 0) {
				if(json.has("rotation")) {
					String rotation = json.getString("rotation");
					mEngine.executeRotation(rotation);
					
					if(mDoResult || (isMain && isBaseEngine()))
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
					addResultOK(json);
				}
				else if(json.has("orientation")) {
					String orientation = json.getString("orientation");
					mEngine.executeOrientation(orientation);
					
					if(mDoResult || (isMain && isBaseEngine()))
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
					addResultOK(json);
				}
			}
			else if(method.compareToIgnoreCase("key") == 0) {
				if(json.has("keycode")) {
					String keycode = json.getString("keycode");
					mEngine.sendDeviceKeyEvent(keycode);
					
					if(mDoResult || (isMain && isBaseEngine()))
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
					addResultOK(json);
				}
				else if(json.has("keys")) {
					JSONArray jsonKeys  = json.getJSONArray("keys");
					mClientService.addDoKeys(jsonKeys);
					
					if(mDoResult || (isMain && isBaseEngine()))
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
					addResultOK(json);
				}
				else if(json.has("text")) {
					String text = json.getString("text");
					if(text.startsWith("!@")) {
						String text2 = text.substring(2);
						if(!text2.isEmpty()) {
							String[] texts = text2.split("\\|");
							if(texts.length > 0) {
								int uxidx = mEngine.getUxIdx();
								if(uxidx < 0)
									uxidx = 0;
								
								uxidx %= texts.length;
								text = texts[uxidx];
							}
						}
					}
					mClientService.addDoText(text);
					
					if(mDoResult || (isMain && isBaseEngine()))
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
					addResultOK(json);
				}
			}
			else if(method.compareToIgnoreCase("text") == 0) {
				String text = "";
				if(json.has("text")) {
					text = json.getString("text");
					if(text.startsWith("!@")) {
						String text2 = text.substring(2);
						if(!text2.isEmpty()) {
							String[] texts = text2.split("\\|");
							if(texts.length > 0) {
								int uxidx = mEngine.getUxIdx();
								if(uxidx < 0)
									uxidx = 0;
								
								uxidx %= texts.length;
								text = texts[uxidx];
							}
						}
					}
				}
				
				if(json.has("element")) {
					if(json.has("text") && json.has("clear")) {
						if(json.has("focus")) {
							mClientService.addSetTextFocus(this, writer, json, text, json.getBoolean("clear"));
						}
						else if(json.has("cache")) {
							mClientService.addSetTextCache(this, writer, json, text, json.getBoolean("clear"));
						}
						else if(json.has("xpath")) {
							mClientService.addSetTextXPath(this, writer, json, json.getString("xpath"), text, json.getBoolean("clear"));
						}
						else if(json.has("x") && json.has("y")) {
							int x = mEngine.PX(json.getInt("x"));
							int y = mEngine.PY(json.getInt("y"));
							mClientService.addSetTextCoordinate(this, writer, json, x, y, text, json.getBoolean("clear"));
						}
					}
					else if(json.has("clear")) {
						if(json.has("focus")) {
							mClientService.addClearTextFocus(this, writer, json);
						}
						else if(json.has("cache")) {
							mClientService.addClearTextCache(this, writer, json);
						}
						else if(json.has("xpath")) {
							mClientService.addClearTextXPath(this, writer, json, json.getString("xpath"));
						}
						else if(json.has("x") && json.has("y")) {
							int x = mEngine.PX(json.getInt("x"));
							int y = mEngine.PY(json.getInt("y"));
							mClientService.addClearTextCoordinate(this, writer, json, x, y);
						}
					}
					else {
						if(json.has("focus")) {
							mClientService.addGetTextFocus(this, writer, json);
						}
						else if(json.has("cache")) {
							mClientService.addGetTextCache(this, writer, json);
						}
						else if(json.has("xpath")) {
							mClientService.addGetTextXPath(this, writer, json, json.getString("xpath"));
						}
						else if(json.has("x") && json.has("y")) {
							int x = mEngine.PX(json.getInt("x"));
							int y = mEngine.PY(json.getInt("y"));
							mClientService.addGetTextCoordinate(this, writer, json, x, y);
						}
					}
				}
				else if(json.has("text")) {
					mClientService.addDoText(text);
					
					if(mDoResult || (isMain && isBaseEngine()))
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
					addResultOK(json);
				}
			}
			else if(method.compareToIgnoreCase("crop") == 0) {
				imgLoc = mEngine.doActionCropEvent(wrap, method, json);
				if(imgLoc != null && imgLoc.getQueryImage() != null) {
					if(isBaseEngine() && debug) {
						BufferedImage cropImage = imgLoc.getQueryImage();
						int cx = imgLoc.getCX(), cy = imgLoc.getCY();
						int crw = imgLoc.getWidth(), crh = imgLoc.getHeight();
						
						try {
							ImageIO.write(cropImage, "jpg", new File("only_crop.jpg"));
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
						}
						
						PrintWriter out = null;
						try {
							Window window = mEngine.getWindow();
							
							JSONObject jsonRoot = new JSONObject();
							JSONObject jsonCrop = new JSONObject();
							JSONObject jsonDev = new JSONObject();
							
							jsonCrop.put("cx", cx);
							jsonCrop.put("cy", cy);
							jsonCrop.put("width", crw);
							jsonCrop.put("height", crh);
							jsonCrop.put("scale", 3.0);
							jsonRoot.put("crop", jsonCrop);
			
							jsonDev.put("current_width", window.getCurrentWidth());
							jsonDev.put("current_height", window.getCurrentHeight());
							jsonDev.put("virtual_width", window.getVirtualWidth());
							jsonDev.put("virtual_height", window.getVirtualHeight());
							jsonDev.put("rotation", window.getRotation());
							jsonDev.put("density", window.getDensity());
							jsonDev.put("dpix", window.getDpiX());
							jsonDev.put("dpiy", window.getDpiY());
							jsonRoot.put("device", jsonDev);
							
							out = new PrintWriter("only_crop.json");
							out.print(jsonRoot.toString(2));
						} catch (IOException e) {
							e.printStackTrace();
						} catch (JSONException e) {
							e.printStackTrace();
						} finally {
							if(out != null)
								out.close();
						}
					}
					
					if(mDoResult || (isMain && isBaseEngine())) {
						JSONObject jsonRes = new JSONObject(json.toString());
						jsonRes.put("x", imgLoc.getX());
						jsonRes.put("y", imgLoc.getY());
						jsonRes.put("crw", imgLoc.getWidth());
						jsonRes.put("crh", imgLoc.getHeight());
						jsonRes.put("query_image", imgLoc.getQueryImageBase64());
						
						AnsiLog.d("[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "crop query_image. x:" + imgLoc.getX() + ", y:" + imgLoc.getY() + ", w:" + imgLoc.getWidth() + ", w:" + imgLoc.getHeight());
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), jsonRes));
						addResultOK(jsonRes, imgLoc.getShotImageBase64());
					}
				}
				else {
					int x = 0, y = 0, cx = 0, cy = 0, crw = 0, crh = 0;
					boolean shot = !json.has("crw") && !json.has("crw");
					boolean center = json.has("cx") && json.has("cy");
					
					try {
						crw = json.getInt("crw");
						crh = json.getInt("crh");

						if(center) {
							cx = json.getInt("cx");
							cy = json.getInt("cy");
						}
						else {
							x = json.getInt("x");
							y = json.getInt("y");
						}
					} catch(JSONException e) {}

					if(shot)
						log.error(9106, String.format("Could not shot"));
					else if(center)
						log.error(9107, String.format("Could not crop coordinates (cx: %d, cy: %d - crw: %d, crh: %d)", cx, cy, crw, crh));
					else
						log.error(9108, String.format("Could not crop coordinates (x: %d, y: %d - crw: %d, crh: %d)", x, y, crw, crh));
					writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
					addResult(log.getErrorCode(), log.getErrorMsg(), json);
				}
			}
			else if(method.compareToIgnoreCase("find") == 0) {
				if(json.has("element")) {
					if(json.has("focus")) {
						mClientService.addGetFocus(this, writer, json);
					}
					else if(json.has("cache")) {
						mClientService.addGetCache(this, writer, json);
					}
					else if(json.has("xpath")) {
						mClientService.addFindXPath(this, writer, json, json.getString("xpath"));
					}
					else if(json.has("x") && json.has("y")) {
						int x = mEngine.PX(json.getInt("x"));
						int y = mEngine.PY(json.getInt("y"));
						mClientService.addFindCoordinate(this, writer, json, x, y);
					}
				}
				else {
					imgLoc = mEngine.doActionFindEvent(wrap, method, json, queryImage);
					if(!mEngine.isCancelFind) {
						if(imgLoc != null && imgLoc.getQueryImage() != null) {
							JSONObject jsonRes = new JSONObject(json.toString());
							jsonRes.put("method", method);
							jsonRes.put("cx", imgLoc.getCX());
							jsonRes.put("cy", imgLoc.getCY());
							jsonRes.put("crw", imgLoc.getWidth());
							jsonRes.put("crh", imgLoc.getHeight());
							jsonRes.put("query_image", imgLoc.getQueryImageBase64());
							
							AnsiLog.d("[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "find query_image. cx:" + imgLoc.getCX() + ", cy:" + imgLoc.getCY());
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), jsonRes));
							addResultOK(jsonRes, imgLoc.getShotImageBase64());
						}
						else {
							log.error(9117, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "The query image could not be found.");
							writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
							addResult(log.getErrorCode(), log.getErrorMsg(), json);
						}
					}
				}
			}
			else if(method.compareToIgnoreCase("autotap") == 0 || method.compareToIgnoreCase("autofind") == 0) {
				imgLoc = mEngine.doActionAutoEvent(wrap, method, json, queryImage);
				if(!mEngine.isCancelAutoSwipe) {
					if(imgLoc != null && imgLoc.getQueryImage() != null) {
						if(mDoResult || (isMain && isBaseEngine()) || method.compareToIgnoreCase("autofind") == 0) {
							JSONObject jsonRes = new JSONObject(json.toString());
							jsonRes.put("method", method);
							jsonRes.put("cx", imgLoc.getCX());
							jsonRes.put("cy", imgLoc.getCY());
							jsonRes.put("crw", imgLoc.getWidth());
							jsonRes.put("crh", imgLoc.getHeight());
							jsonRes.put("query_image", imgLoc.getQueryImageBase64());

							AnsiLog.d("[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "find query_image. cx:" + imgLoc.getCX() + ", cy:" + imgLoc.getCY());
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), jsonRes));
							addResultOK(jsonRes, imgLoc.getShotImageBase64());
						}
					}
					else {
						log.error(9116, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "The query image could not be found.");
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						addResult(log.getErrorCode(), log.getErrorMsg(), json);
					}
				}
			}
			else if(method.compareToIgnoreCase("shell") == 0) {
				String key = json.getString("key");
				String cmd = json.getString("shell");
				String output;
				if(key.compareToIgnoreCase("logcat") == 0) {
					if(json.has("package") && !json.getString("package").isEmpty()) {
						String pkg = json.getString("package");
						if(pkg.equals("BQ_CURRENT_APP")) {
							JSONObject jsonTop = mEngine.getTopActivity();
							if(jsonTop != null) {
								pkg = jsonTop.getString("package");
								json.put("package", pkg);
							}
						}
						
						String ps = "ps | grep " + pkg;
						output = mEngine.executeShellCommand(ps).trim();
						if(output.length() > 15) {
							cmd += " | grep -E \"\\.[0-9][0-9][0-9] " + output.substring(10, 15) + "\"";
						}
					}
				}
				
				output = mEngine.executeShellCommand(cmd).trim();
				try {
					output = URLEncoder.encode(output, "UTF-8");
				} catch(Exception e) {}
				
				if(!json.has("package"))
					json.put("package", "");
				if(json.has("log") && json.getBoolean("log"))
					setLog(json, output);
				else
					json.put("output", output);
				
				writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
			}
			else if(method.compareToIgnoreCase("app") == 0) {
				String submethod = json.getString("submethod");
				
				if(submethod.compareToIgnoreCase("install") == 0) {
					String app_file = json.getString("app_file");
					json.remove("app_file");
					
					String output = mEngine.installPackage(app_file, true);
					if(output.equals("OK")) {
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
						addResultOK(json);
					}
					else {
						log.error(9121, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + output);
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
					}
				}
				else if(submethod.compareToIgnoreCase("uninstall") == 0) {
					String output = "";
					String pkg = json.getString("package");
					if(pkg.equals("BQ_CURRENT_APP")) {
						JSONObject jsonTop = mEngine.getTopActivity();
						if(jsonTop != null) {
							pkg = jsonTop.getString("package");
							json.put("package", pkg);
							json.put("pkg_info", jsonTop);
							output = "OK";
						}					
					}
					else {
						output = mEngine.isInstallPackage(pkg);
						if(output.equals("OK"))
							json.put("pkg_info", mEngine.getPkgInfo(pkg));
					}
					
					if(output.equals("OK")) {
						output = mEngine.uninstallPackage(pkg);
						if(output.equals("OK")) {
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
							addResultOK(json);
						}
						else {
							log.error(9123, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + output);
							writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						}
					}
					else {
						log.error(9122, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + output);
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
					}
				}
				else if(submethod.compareToIgnoreCase("run") == 0) {
					String output = "";
					String pkg = json.getString("package");
					if(pkg.equals("BQ_CURRENT_APP")) {
						JSONObject jsonTop = mEngine.getTopActivity();
						if(jsonTop != null) {
							pkg = jsonTop.getString("package");
							json.put("package", pkg);
							json.put("pkg_info", jsonTop);
							output = "OK";
						}					
					}
					else {
						output = mEngine.isInstallPackage(pkg);
						if(output.equals("OK"))
							json.put("pkg_info", mEngine.getPkgInfo(pkg));
					}
					
					if(output.equals("OK")) {
						String service = null, intent = null, activity = null;
						if(json.has("service"))
							service = json.getString("service");
						if(json.has("intent"))
							intent = json.getString("intent");
						if(json.has("activity"))
							activity = json.getString("activity");
						if(service != null)
							mEngine.runPackageService(pkg, service, intent);
						else
							mEngine.runPackageApp(pkg, activity, intent);
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
						addResultOK(json);
					}
					else {
						log.error(9122, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + output);
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
					}
				}
				else if(submethod.compareToIgnoreCase("stop") == 0) {
					String output = "";
					String pkg = json.getString("package");
					if(pkg.equals("BQ_CURRENT_APP")) {
						JSONObject jsonTop = mEngine.getTopActivity();
						if(jsonTop != null) {
							pkg = jsonTop.getString("package");
							json.put("package", pkg);
							json.put("pkg_info", jsonTop);
							output = "OK";
						}
					}
					else {
						output = mEngine.isInstallPackage(pkg);
						if(output.equals("OK"))
							json.put("pkg_info", mEngine.getPkgInfo(pkg));
					}
					
					if(output.equals("OK")) {
						String service = null, intent = null;
						if(json.has("service"))
							service = json.getString("service");
						if(json.has("intent"))
							intent = json.getString("intent");
						if(service != null)
							mEngine.stopPackageService(pkg, service, intent);
						else
							mEngine.stopPackageApp(pkg);
						writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
						addResultOK(json);
					}
					else {
						log.error(9122, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + output);
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
					}
				}
				else if(submethod.compareToIgnoreCase("info") == 0) {
					String pkg = json.getString("package");
					if(pkg.equals("BQ_CURRENT_APP")) {
						JSONObject jsonTop = mEngine.getTopActivity();
						if(jsonTop != null) {
							pkg = jsonTop.getString("package");
							json.put("package", pkg);
							json.put("pkg_info", jsonTop);
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
						}
						else {
							log.error(9124, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] Not Found Top Activity");
							writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						}
					}
					else {
						String output = mEngine.isInstallPackage(pkg);
						if(output.equals("OK")) {
							json.put("pkg_info", mEngine.getPkgInfo(pkg));
							
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), json));
						}
						else {
							log.error(9122, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + output);
							writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						}
					}
				}
			}
			else {
				if(!isMain && isBaseEngine()) {
					AnsiLog.i2("[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "Base Device skipped!!!");
					return;
				}
				if(method.compareToIgnoreCase("click") == 0 && json.has("element")) {
					if(json.has("focus")) {
						mClientService.addClickFocus(this, writer, json);
					}
					else if(json.has("cache")) {
						mClientService.addClickCache(this, writer, json);
					}
					else if(json.has("xpath")) {
						mClientService.addClickXPath(this, writer, json, json.getString("xpath"));
					}
					else if(json.has("x") && json.has("y")) {
						int x = mEngine.PX(json.getInt("x"));
						int y = mEngine.PY(json.getInt("y"));
						mClientService.addClickCoordinate(this, writer, json, x, y);
					}
				}
				else if(json.has("cx") && json.has("cy") && queryImage == null) {
					int cx = json.getInt("cx");
					int cy = json.getInt("cy");
					int crw = 0, crh = 0;
					SoftReference<BufferedImage> cropImage = null;
					
					if(json.has("crw") && json.has("crh")) {
						crw = json.getInt("crw");
						crh = json.getInt("crh");
					}
					mEngine.doActionTouchEvent(wrap, method, cx, cy);
					
					MirrorEngine baseEngine = wrap.getBaseEngine();
					if(crw > 0 && crh > 0)
						cropImage = baseEngine.getCenterCropDetectImage(cx, cy, crw, crh);
					
					if(cropImage != null) {
						if(isBaseEngine() && debug) {
							try {
								ImageIO.write(cropImage.get(), "jpg", new File("tap_crop.jpg"));
							} catch (IOException e) {
								e.printStackTrace();
							} finally {
							}
							
							PrintWriter out = null;
							try {
								Window window = baseEngine.getWindow();
								
								JSONObject json = new JSONObject();
								JSONObject jsonCrop = new JSONObject();
								JSONObject jsonDev = new JSONObject();
								
								jsonCrop.put("cx", cx);
								jsonCrop.put("cy", cy);
								jsonCrop.put("width", crw);
								jsonCrop.put("height", crh);
								jsonCrop.put("scale", 3.0);
								json.put("crop", jsonCrop);
				
								jsonDev.put("current_width", window.getCurrentWidth());
								jsonDev.put("current_height", window.getCurrentHeight());
								jsonDev.put("virtual_width", window.getVirtualWidth());
								jsonDev.put("virtual_height", window.getVirtualHeight());
								jsonDev.put("rotation", window.getRotation());
								jsonDev.put("density", window.getDensity());
								jsonDev.put("dpix", window.getDpiX());
								jsonDev.put("dpiy", window.getDpiY());
								json.put("device", jsonDev);
								
								out = new PrintWriter("tap_crop.json");
								out.print(json.toString(2));
							} catch (IOException e) {
								e.printStackTrace();
							} catch (JSONException e) {
								e.printStackTrace();
							} finally {
								if(out != null)
									out.close();
							}
						}
					}
					
					if(crw > 0 && crh > 0 && cropImage == null) {
						log.error(9110, String.format("[method:" + method + "] " + "Could not crop coordinates (cx: %d, cy: %d - crw: %d, crh: %d)", cx, cy, crw, crh));
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						addResult(log.getErrorCode(), log.getErrorMsg(), json);
					}
					else {
						if(mDoResult || (isMain && isBaseEngine())) {
							JSONObject jsonRes = new JSONObject(json.toString());
							jsonRes.put("method", method);
							if(cropImage != null)
								jsonRes.put("query_image", ImageUtil.encodeByBase64(cropImage.get()));
	
							AnsiLog.d("[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "find cx:" + cx + ", cy:" + cy);
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), jsonRes));
							addResultOK(jsonRes);
						}
					}
				}
				else if(queryImage != null) {
					imgLoc = mEngine.doActionTouchEvent(wrap, method, json, queryImage);

					if(imgLoc != null && imgLoc.getQueryImage() != null) {
						if(mDoResult || (isMain && isBaseEngine())) {
							JSONObject jsonRes = new JSONObject(json.toString());
							jsonRes.put("method", method);
							jsonRes.put("cx", imgLoc.getCX());
							jsonRes.put("cy", imgLoc.getCY());
							jsonRes.put("crw", imgLoc.getWidth());
							jsonRes.put("crh", imgLoc.getHeight());
							jsonRes.put("query_image", imgLoc.getQueryImageBase64());

							AnsiLog.d("[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "find query_image. cx:" + imgLoc.getCX() + ", cy:" + imgLoc.getCY());
							writer.write(AbstractRemoteCommand.success(method, mEngine.getDeviceSerial(), jsonRes));
							addResultOK(jsonRes, imgLoc.getShotImageBase64());
						}
					}
					else if(imgLoc != null) {
						log.error(9112, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "The method works in the specified position according to the coordinate ratio.");
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						addResult(log.getErrorCode(), log.getErrorMsg(), json);
					}
					else {
						log.error(9113, "[" + mEngine.getDeviceModel() + "] " + "[method:" + method + "] " + "The query image could not be found.");
						writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
						addResult(log.getErrorCode(), log.getErrorMsg(), json);
					}
				}
				else {
					log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + json.toString());
					writer.write(AbstractRemoteCommand.failed(method, mEngine.getDeviceSerial(), json, log.getErrorMsg(), log.getErrorCode()));
				}
			}
			
			if(imgLoc != null)
				imgLoc.clearImage();
		} catch(InterruptedException e) {
		} catch(JSONException e) {}
	}
    
}
