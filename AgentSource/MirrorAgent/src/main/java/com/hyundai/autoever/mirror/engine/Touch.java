package com.hyundai.autoever.mirror.engine;

public class Touch {
	@Override
	public String toString() {
		return "Touch [version=" + version + ", pid=" + pid
				+ ", max_contacts=" + max_contacts
				+ ", max_x=" + max_x + ", max_y=" + max_y
				+ ", max_pressure=" + max_pressure + "]";
	}
	public int getVersion() {
		return version;
	}
	public void setVersion(int version) {
		this.version = version;
	}
	public int getPid() {
		return pid;
	}
	public void setPid(int pid) {
		this.pid = pid;
	}
	public int getMaxContacts() {
		return max_contacts;
	}
	public void setMaxContacts(int max_contacts) {
		this.max_contacts = max_contacts;
	}
	public int getMaxX() {
		return max_x;
	}
	public void setMaxX(int max_x) {
		this.max_x = max_x;
	}
	public int getMaxY() {
		return max_y;
	}
	public void setMaxY(int max_y) {
		this.max_y = max_y;
	}
	public int getMaxPressure() {
		return max_pressure;
	}
	public void setMaxPressure(int max_pressure) {
		this.max_pressure = max_pressure;
	}
	public void reset() {
		version = 0;
		max_contacts = 0;
		max_x = 0;
		max_y = 0;
		max_pressure = 0;
		pid = 0;
	}
	private int version = 0;
	private int max_contacts = 0;
	private int max_x = 0, max_y = 0;
	private int max_pressure = 0;
	private int pid = 0;
}
