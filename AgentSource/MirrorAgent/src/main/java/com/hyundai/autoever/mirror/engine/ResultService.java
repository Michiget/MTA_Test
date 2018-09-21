package com.hyundai.autoever.mirror.engine;

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.net.URLEncoder;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.hyundai.autoever.utils.ImageUtil;

public class ResultService implements Runnable {
	private static Logger logger = Logger.getLogger("logger_upload_result");
	
	private final int 
		TYPE_RESULT = 0,
		TYPE_LOG = 1;
	private MirrorEngine mEngine = null;
	private Queue<SoftReference<JSONObject>> mResultQueue = new LinkedBlockingQueue<SoftReference<JSONObject>>();
	
	public ResultService(MirrorEngine engine) {
		mEngine = engine;
	}
	
	public void add(JSONObject json) {
		mResultQueue.add(new SoftReference<JSONObject>(json));
	}
	
	public void add(SoftReference<JSONObject> json) {
		mResultQueue.add(json);
	}
	
	public void add(int idx, int code, String msg, JSONObject scriptData, BufferedImage shotImage, int rotation) {
		add(idx, code, msg, scriptData, shotImage != null ? ImageUtil.encodeByBase64(shotImage) : null, rotation);
	}
	
	public void add(int idx, int code, String msg, JSONObject scriptData, String shotImageBase64, int rotation) {
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", TYPE_RESULT);
			json.get().put("idx", idx);
			json.get().put("resultcode", code);
			json.get().put("resultmsg", msg);
			json.get().put("script",  scriptData.toString());
			json.get().put("rotation", rotation);
			if(shotImageBase64 != null)
				json.get().put("image", shotImageBase64);
			
			if(ServerModule.get().isIncludeCpuMemSetResult()) {
				JSONObject jsonRes = mEngine.getResourceStatus(null, 0, false);
				if(jsonRes.has("data")) {
					JSONObject jsonData = jsonRes.getJSONObject("data");
					JSONObject jsonCpu = new JSONObject();
					JSONObject jsonMem = new JSONObject();
					
					String[] cpuKeys = {"cpu_total_percent", "cpu_user_percent", "cpu_kernel_percent"};
					for(int j=0;j<cpuKeys.length;j++) {
						if(jsonData.has(cpuKeys[j]))
							jsonCpu.put(cpuKeys[j], jsonData.getDouble(cpuKeys[j]));
					}
					
					String[] memKeys = {"mem_total_kbyte", "mem_free_kbyte", "mem_used_kbyte", "mem_lost_kbyte"};
					for(int j=0;j<memKeys.length;j++) {
						if(jsonData.has(memKeys[j]))
							jsonMem.put(memKeys[j], jsonData.getDouble(memKeys[j]));
					}
					
					if(jsonCpu.length() > 0)
						json.get().put("cpu",  jsonCpu.toString());
					if(jsonMem.length() > 0)
						json.get().put("memory",  jsonMem.toString());
				}
			}
			add(json);
		} catch(JSONException e) {
			logger.error(e);
		}
	}
	
	public void setLog(int idx, JSONObject scriptData, String output) {
		SoftReference<JSONObject> json = new SoftReference<JSONObject>(new JSONObject());
		try {
			json.get().put("type", TYPE_LOG);
			json.get().put("idx", idx);
			json.get().put("script",  scriptData.toString());
			json.get().put("output", output);
			requestSetLog(json.get());
		} catch(Exception e) {}
	}
	
	@Override
	public void run() {
		Thread thread = Thread.currentThread();
		
		while(mEngine.isRunning() && !thread.isInterrupted()) {
			try {
				if(mResultQueue.isEmpty()) {
					Thread.sleep(10);
					continue;
				}
					
				SoftReference<JSONObject> obj = mResultQueue.poll();
				int type = obj.get().getInt("type");
				if(type == TYPE_RESULT)
					requestSetResult(obj.get());
				else if(type == TYPE_LOG)
					requestSetLog(obj.get());
				obj.clear();
				
				Thread.sleep(1);
			} catch(Exception e) {
				
			}
		}
	}
	
	private void requestSet(String url, JSONObject jsonRoot) {
		int timeout = 30;
		RequestConfig config = RequestConfig.custom().
		  setConnectTimeout(timeout * 1000).
		  setConnectionRequestTimeout(timeout * 1000).
		  setSocketTimeout(timeout * 1000).build();
		HttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build(); //Use this instead
		try {
			int type = jsonRoot.getInt("type");
			if(type == TYPE_RESULT)
				logger.debug("{result}");
			else
				logger.debug("{log}");
			jsonRoot.remove("type");
			
		    String json = jsonRoot.toString();
		    
		    JSONObject jsonLog = new JSONObject(json);
		    jsonLog.remove("image");
		    logger.debug(jsonLog.toString());
		    
		    HttpPost request = new HttpPost(url);
		    StringEntity params = new StringEntity(json+"\n");
		    request.addHeader("content-type", "application/json");
		    request.setEntity(params);
		    HttpResponse httpResponse = httpClient.execute(request);
		    HttpEntity httpEntity = httpResponse.getEntity();
		    if(httpEntity != null) {
//		    	String sText = EntityUtils.toString(httpEntity, "UTF-8");
//			    System.out.println("response : " + sText);
		    }
		} catch (Exception ex) {
			logger.error(ex);
		} finally {
		}				
	}
	
	private void requestSetResult(JSONObject jsonRoot) {
		requestSet(ServerModule.get().getUrlSetResult(), jsonRoot);
	}
	
	private void requestSetLog(JSONObject jsonRoot) {
		requestSet(ServerModule.get().getUrlSetLog(), jsonRoot);
	}
	
}
