package com.hyundai.autoever.utils;

import org.fusesource.jansi.AnsiConsole;
import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

public class AnsiLog {
	private static Logger logger = Logger.getLogger(AnsiLog.class);
	private static SimpleDateFormat df = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS] ");
	
	private int mErrCode = 0;
	private String mErrMsg = "";

	public static void install() {
		AnsiConsole.systemInstall();
	}
	public static void uninstall() {
		AnsiConsole.systemUninstall();
	}
	
	private static String getNow() {
		String d = "";
		try {
			d = df.format(new Date(System.currentTimeMillis()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return d;
	}
	
	public static void l(String s) {
		System.out.println(getNow()+s);
		logger.debug(s);
	}
	
	public static void d(String s) {
		System.out.println(getNow()+"[DEBUG-0000] " + s);
		logger.debug("[DEBUG-0000] " + s);
	}
	
	public static void dp(String s) {
		System.out.println(getNow()+"[DEBUG-0000] " + s);
	}
	
	public static void h(String s) {
		System.out.println(ansi().bg(WHITE).fg(BLACK).a(getNow()+s).reset());
		logger.info(s);
	}

	public static void br(int len) {
		String blk = ""; for(int i=0;i<len;i++) blk += " ";
		System.out.println(ansi().bg(WHITE).fg(RED).bold().newline().a(blk).reset());
	}

	public static void er(int len) {
		String blk = ""; for(int i=0;i<len;i++) blk += " ";
		System.out.println(ansi().bg(WHITE).fg(RED).bold().a(blk).newline().reset());
	}
	
	public static void r(String s, int mw) {
		while(s.length() < mw)
			s += " ";
		System.out.println(ansi().bg(WHITE).fg(RED).bold().a(s).reset());
		logger.info(s);
	}
	
	public static void i(String s) {
		String msg = String.format("[INFO1-0000] %s", s);
		System.out.println(ansi().fg(WHITE).bold().a(getNow()+msg).reset());
		logger.info(msg);
	}
	
	public static void i(int c, String s) {
		String msg = String.format("[INFO1-%04d] %s", c, s);
		System.out.println(ansi().fg(WHITE).bold().a(getNow()+msg).reset());
		logger.info(msg);
	}
	
	public static void i2(String s) {
		String msg = String.format("[INFO2-0000] %s", s);
		System.out.println(ansi().fg(GREEN).bold().a(getNow()+msg).reset());
		logger.info(msg);
	}
	
	public static void i2(int c, String s) {
		String msg = String.format("[INFO2-%04d] %s", c, s);
		System.out.println(ansi().fg(GREEN).bold().a(getNow()+msg).reset());
		logger.info(msg);
	}
	
	public static void i3(String s) {
		String msg = String.format("[INFO3-0000] %s", s);
		System.out.println(ansi().fg(CYAN).bold().a(getNow()+msg).reset());
		logger.info(msg);
	}
	
	public static void i3(int c, String s) {
		String msg = String.format("[INFO3-%04d] %s", c, s);
		System.out.println(ansi().fg(CYAN).bold().a(getNow()+msg).reset());
		logger.info(msg);
	}
	
	public static void w(String s) {
		String msg = String.format("[WARN1-0000] %s", s);
		System.out.println(ansi().fg(YELLOW).bold().a(getNow()+msg).reset());
		logger.warn(msg);
	}
	
	public static void w(int c, String s) {
		String msg = String.format("[WARN1-%04d] %s", c, s);
		System.out.println(ansi().fg(YELLOW).bold().a(getNow()+msg).reset());
		logger.warn(msg);
	}
	
	public static void e(String s) {
		String msg = String.format("[ERROR-0000] %s", s);
		System.out.println(ansi().fg(RED).bold().a(getNow()+msg).reset());
		logger.error(msg);
	}
	
	public static void e(int c, String s) {
		String msg = String.format("[ERROR-%04d] %s", c, s);
		System.out.println(ansi().fg(RED).bold().a(getNow()+msg).reset());
		logger.error(msg);
	}

	public void error(String s) {
		mErrCode = 0;
		mErrMsg = String.format("[ERROR-0000] %s", s);
		System.out.println(ansi().fg(RED).bold().a(getNow()+mErrMsg).reset());
		logger.error(mErrMsg);
	}
	
	public void error(int c, String s) {
		mErrCode = c;
		mErrMsg = String.format("[ERROR-%04d] %s", c, s);
		System.out.println(ansi().fg(RED).bold().a(getNow()+mErrMsg).reset());
		logger.error(mErrMsg);
	}
	
	public void clearError() {
		mErrCode = 0;
		mErrMsg = "";
	}
	
	public int getErrorCode() {
		return mErrCode;
	}
	
	public String getErrorMsg() {
		return mErrMsg;
	}
}
