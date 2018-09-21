package com.hyundai.autoever.mirror.engine.query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.ServerModule;
import com.hyundai.autoever.utils.AndroidUtil;

public class AppScript extends AbstractRemoteCommand {
	public AppScript(CommandServer.ConnectionCommandWrap wrap) {
		super(wrap);
	}

	@Override
	public JSONObject respond(JSONObject args) throws IOException, JSONException {
		init(args);
		
		if(!args.has("submethod"))
			log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
		else {
			String submethod = args.getString("submethod");
			if(submethod.equals("install")) {
				if(ServerModule.get().getUrlGetApp() != null) {
					if(args.has("idx")) {
						download(args);
					}
					else 
						log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
				}
				else
					log.error(9103, "[method:" + method + "] " + "not supported method : " + args.toString());
			}
			else {
				if(args.has("package")) {
					String validsub = "uninstall run stop info";
					if(!validsub.contains(submethod))
						log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
					else
						action();
				}
				else
					log.error(9101, "[method:" + method + "] " + "Invalid Protocol : " + args.toString());
			}
		}
		
		if(log.getErrorCode() > 0)
			return failed(method, args, log.getErrorMsg(), log.getErrorCode());
		return null;
	}

	private void download(final JSONObject json) {
		mExecutorScript.submit(new Runnable() {
			public void run() {
				int idx = 0;
				try { idx = json.getInt("idx"); } catch(JSONException e) {}
				String file = getFile(json);
				if(file == null) {
					log.error(9120, String.format("Could not download app (idx:%d)", idx));
					wrap.getWriter().write(AbstractRemoteCommand.failed(method, json, log.getErrorMsg(), log.getErrorCode()));
				}
				else {
					try {
						json.put("app_info", AndroidUtil.getApkInfo(file));
						json.put("app_file", file);
						
						action();
					} catch(JSONException e) {}
				}
			}
		});
	}
	
	private String getFile(JSONObject scriptData) {
		JSONObject json = new JSONObject();
		try {
			json.put("script",  scriptData.toString());
			return requestGetFile(ServerModule.get().getUrlGetApp(), json);
		} catch(Exception e) {}
		return null;
	}
	
	private String requestGetFile(String url, JSONObject jsonRoot) {
		String filePath = null;
		HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead
		try {
		    HttpPost request = new HttpPost(url);
		    String json = jsonRoot.toString() + "\n";
		    StringEntity params = new StringEntity(json);
		    request.addHeader("content-type", "application/json");
		    request.setEntity(params);
		    HttpResponse httpResponse = httpClient.execute(request);
		    HttpEntity httpEntity = httpResponse.getEntity();
		    if(httpEntity != null) {
//		    	String sText = EntityUtils.toString(httpEntity, "UTF-8");
//			    System.out.println("response : " + sText);
//			    return null;
		    	SimpleDateFormat df = new SimpleDateFormat("_yyyyMMdd_HHmmss");
		    	String localPath = System.getProperty("user.dir") + File.separator + "down"; 
		    	String fileName = "installApp", fileExt = ".apk", fileNameExt = fileName + fileExt;
		    	String disposition = httpResponse.getFirstHeader("Content-Disposition").getValue();
		    	if (disposition != null) {
	                // extracts file name from header field
	                int index = disposition.toLowerCase().indexOf("filename=");
	                if (index > 0) {
	                	fileNameExt = disposition.substring(index + 10, disposition.length() - 1);
	                }
	            }
		    	
				try {
					int n = fileNameExt.lastIndexOf('.');
					if(n >= 0) {
						if(n > 0)
							fileName = fileNameExt.substring(0, n);
						fileExt = fileNameExt.substring(n);
						fileNameExt = fileName + df.format(new Date(System.currentTimeMillis())) + fileExt;
					}
				} catch (Exception e) {}
		    	
				File dir = new File(localPath);
				if(!dir.exists())
					dir.mkdirs();
				filePath = localPath + File.separator + fileNameExt;
		        FileOutputStream fos = new FileOutputStream(filePath);
		        httpEntity.writeTo(fos);
		        fos.close();
		    }
		} catch (Exception ex) {
		} finally {
		}
		return filePath;
	}
}
