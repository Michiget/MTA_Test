package com.hyundai.autoever.mirror.engine;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class Window {
	@Override
	public String toString() {
		return "Window [displayId=" + displayId
				+ ", name=" + name+ ", serial=" + serial + ", model=" + model
				+ ", sensor=" + sensor+ ", isInputRotation=" + isInputRotation+ ", rotation=" + rotation
				+ ", density=" + density+ ", dpiX=" + dpiX + ", dpiY=" + dpiY
				+ ", physicalWidth=" + physicalWidth + ", physicalHeight=" + physicalHeight
				+ ", currentWidth=" + currentWidth + ", currentHeight=" + currentHeight
				+ ", appWidth=" + appWidth + ", appHeight=" + appHeight
				+ ", virtualWidth=" + virtualWidth + ", virtualHeight=" + virtualHeight
				+ "]";
	}
	public int getDisplayId() {
		return displayId;
	}
	public void setDisplayId(int displayId) {
		this.displayId = displayId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		if(name != null)
			this.name = name;
	}
	public String getSerial() {
		return serial;
	}
	public void setSerial(String serial) {
		if(serial != null)
			this.serial = serial;
	}
	public String getModel() {
		return model;
	}
	public void setModel(String model) {
		if(model != null)
			this.model = model;
	}
	public boolean getSensor() {
		return sensor;
	}
	public void setSensor(boolean sensor) {
		this.sensor = sensor;
	}
	public boolean getInputRotation() {
		return isInputRotation;
	}
	public void setInputRotation(boolean isInputRotation) {
		this.isInputRotation = isInputRotation;
	}
	public int getDensity() {
		return density;
	}
	public void setDensity(int density) {
		this.density = density;
	}
	public double getDpiX() {
		return dpiX;
	}
	public void setDpiX(double dpiX) {
		this.dpiX = dpiX;
	}
	public double getDpiY() {
		return dpiY;
	}
	public void setDpiY(double dpiY) {
		this.dpiY = dpiY;
	}
	public int getRotation() {
		return rotation;
	}
	public void setRotation(int rotation) {
		this.rotation = rotation;
	}
	public void setRotation(int rotation, boolean isInputRotation) {
		this.rotation = rotation;
		setInputRotation(isInputRotation);
	}
	public int getOrientation() {
		return (rotation*90)%360;
	}
	public int getPhysicalWidth() {
		return physicalWidth;
	}
	public void setPhysicalWidth(int physicalWidth) {
		this.physicalWidth = physicalWidth;
	}
	public int getPhysicalHeight() {
		return physicalHeight;
	}
	public void setPhysicalHeight(int physicalHeight) {
		this.physicalHeight = physicalHeight;
	}
	public int getCurrentWidth() {
		return currentWidth;
	}
	public void setCurrentWidth(int currentWidth) {
		this.currentWidth = currentWidth;
		if(this.physicalWidth < this.currentWidth)
			this.physicalWidth = this.currentWidth;
		if(this.virtualWidth == 0)
			this.virtualWidth = currentWidth;
	}
	public int getCurrentHeight() {
		return currentHeight;
	}
	public void setCurrentHeight(int currentHeight) {
		this.currentHeight = currentHeight;
		if(this.physicalHeight < this.currentHeight)
			this.physicalHeight = this.currentHeight;
		if(this.virtualHeight == 0)
			this.virtualHeight = currentHeight;
	}
	public int getAppWidth() {
		return appWidth;
	}
	public void setAppWidth(int appWidth) {
		this.appWidth = appWidth;
	}
	public int getAppHeight() {
		return appHeight;
	}
	public void setAppHeight(int appHeight) {
		this.appHeight = appHeight;
	}
	public int getVirtualWidth() {
		return virtualWidth;
	}
	public void setVirtualWidth(int virtualWidth) {
		this.virtualWidth = virtualWidth;
	}
	public int getVirtualHeight() {
		return virtualHeight;
	}
	public void setVirtualHeight(int virtualHeight) {
		this.virtualHeight = virtualHeight;
	}
	public void fromJson(JSONObject json) {
		try { setName(json.getString("name")); } catch(JSONException e) {}
		try { setSerial(json.getString("serial")); } catch(JSONException e) {}
		try { setModel(json.getString("model")); } catch(JSONException e) {}
		try { setSensor(json.getBoolean("sensor")); } catch(JSONException e) {}
		try { setInputRotation(json.getBoolean("is_input_rotation")); } catch(JSONException e) {}
		try { setRotation(json.getInt("rotation")); } catch(JSONException e) {}
		try { setPhysicalWidth(json.getInt("physical_width")); } catch(JSONException e) {}
		try { setPhysicalHeight(json.getInt("physical_height")); } catch(JSONException e) {}
		try { setCurrentWidth(json.getInt("current_width")); } catch(JSONException e) {}
		try { setCurrentHeight(json.getInt("current_height")); } catch(JSONException e) {}
		try { setAppWidth(json.getInt("app_width")); } catch(JSONException e) {}
		try { setAppHeight(json.getInt("app_height")); } catch(JSONException e) {}
		try { setVirtualWidth(json.getInt("virtual_width")); } catch(JSONException e) {}
		try { setVirtualHeight(json.getInt("virtual_height")); } catch(JSONException e) {}
		try { setDensity(json.getInt("density")); } catch(JSONException e) {}
		try { setDpiX(json.getDouble("dpiX")); } catch(JSONException e) {}
		try { setDpiY(json.getDouble("dpiY")); } catch(JSONException e) {}
	}
	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		try { json.put("name", getName()); } catch(JSONException e) {}
		try { json.put("serial", getSerial()); } catch(JSONException e) {}
		try { json.put("model", getModel()); } catch(JSONException e) {}
		try { json.put("sensor", getSensor()); } catch(JSONException e) {}
		try { json.put("is_input_rotation", getInputRotation()); } catch(JSONException e) {}
		try { json.put("rotation", getRotation()); } catch(JSONException e) {}
		try { json.put("physical_width", getPhysicalWidth()); } catch(JSONException e) {}
		try { json.put("physical_height", getPhysicalHeight()); } catch(JSONException e) {}
		try { json.put("current_width", getCurrentWidth()); } catch(JSONException e) {}
		try { json.put("current_height", getCurrentHeight()); } catch(JSONException e) {}
		try { json.put("app_width", getAppWidth()); } catch(JSONException e) {}
		try { json.put("app_height", getAppHeight()); } catch(JSONException e) {}
		try { json.put("virtual_width", getVirtualWidth()); } catch(JSONException e) {}
		try { json.put("virtual_height", getVirtualHeight()); } catch(JSONException e) {}
		try { json.put("density", getDensity()); } catch(JSONException e) {}
		try { json.put("dpiX", getDpiX()); } catch(JSONException e) {}
		try { json.put("dpiY", getDpiY()); } catch(JSONException e) {}
		
		return json;
	}
	public void reset() {
		displayId = -1;
		serial = "";
		model = "";
		name = "";
		sensor = false;
		isInputRotation = false;
		rotation = 0;
		physicalWidth = 0;
		physicalHeight = 0;
		currentWidth = 0;
		currentHeight = 0;
		appWidth = 0;
		appHeight = 0;
		virtualWidth = 0;
		virtualHeight = 0;
		density = 0;
		dpiX = 0;
		dpiY = 0;
	}
	public void set(Window w) {
		setName(w.getName());
		setSerial(w.getSerial());
		setModel(w.getModel());
		setSensor(w.getSensor());
		setInputRotation(w.getInputRotation());
		setRotation(w.getRotation());
		setPhysicalWidth(w.getPhysicalWidth());
		setPhysicalHeight(w.getPhysicalHeight());
		setCurrentWidth(w.getCurrentWidth());
		setCurrentHeight(w.getCurrentHeight());
		setAppWidth(w.getAppWidth());
		setAppHeight(w.getAppHeight());
		setVirtualWidth(w.getVirtualWidth());
		setVirtualHeight(w.getVirtualHeight());
		setDensity(w.getDensity());
		setDpiX(w.getDpiX());
		setDpiY(w.getDpiY());
	}
	public Window() {
		reset();
	}
	public Window(JSONObject json) {
		reset();
		fromJson(json);
	}
	private int displayId = -1;
	private String serial = "";
	private String model = "";
	private String name = "";
	private boolean sensor = false;
	private boolean isInputRotation = false;
	private int rotation = 0;
	private int physicalWidth = 0;
	private int physicalHeight = 0;
	private int currentWidth = 0;
	private int currentHeight = 0;
	private int appWidth = 0;
	private int appHeight = 0;
	private int virtualWidth = 0;
	private int virtualHeight = 0;
	private int density = 0;
	private double dpiX = 0;
	private double dpiY = 0;
}
