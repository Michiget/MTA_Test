package com.hyundai.autoever.imagerecognition;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;

import com.hyundai.autoever.utils.ImageUtil;

public class ImageLocation {
    private Point topLeft;
    private Point topRight;
    private Point bottomLeft;
    private Point bottomRight;
    private Point center;
    private int scaleFactor;
    private double resizeFactor;
    private SoftReference<BufferedImage> queryImage;
    private String queryImageBase64;
    private SoftReference<BufferedImage> shotImage;
    private String shotImageBase64;
    private String method;
    private int checkMain;

    public ImageLocation(){
    	this.topLeft = new Point(0,0);
    	this.topRight = new Point(0,0);
    	this.bottomLeft = new Point(0,0);
    	this.bottomRight = new Point(0,0);
    	this.center = new Point(0,0);
    	this.queryImage = null;
    	this.queryImageBase64 = null;
    	this.shotImage = null;
    	this.shotImageBase64 = null;
    	this.method = null;
    	
        this.scaleFactor=1;
        this.resizeFactor=1;
        this.checkMain = 0;
    }

    public Point getTopLeft() {
        return topLeft;
    }
    public void setTopLeft(Point topLeft) {
        this.topLeft = topLeft;
        this.topRight.y = this.topLeft.y;
        this.bottomLeft.x = this.topLeft.x;
        
        recalcCenter();
    }

    public Point getTopRight() {
        return topRight;
    }

    public void setTopRight(Point topRight) {
        this.topRight = topRight;
        this.topLeft.y = this.topRight.y;
        this.bottomRight.x = this.topRight.x;
        
        recalcCenter();
    }

    public Point getBottomLeft() {
        return bottomLeft;
    }

    public void setBottomLeft(Point bottomLeft) {
        this.bottomLeft = bottomLeft;
        this.bottomRight.y = this.bottomLeft.y;
        this.topLeft.x = this.bottomLeft.x;
        
        recalcCenter();
    }

    public Point getBottomRight() {
        return bottomRight;
    }

    public void setBottomRight(Point bottomRight) {
        this.bottomRight = bottomRight;
        this.bottomLeft.y = this.bottomRight.y;
        this.topRight.x = this.bottomRight.x;
        
        recalcCenter();
    }

    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }
    
    private void recalcCenter() {
    	if(this.topLeft.x < this.bottomRight.x)
    		this.center.x = (this.bottomRight.x-this.topLeft.x)/2;
    	if(this.topLeft.y < this.bottomRight.y)
    		this.center.y = (this.bottomRight.y-this.topLeft.y)/2;
    }
    
    public void offset(int x, int y) {
    	this.topLeft.x += x;
    	this.topLeft.y += y;

    	this.topRight.x += x;
    	this.topRight.y += y;

    	this.bottomLeft.x += x;
    	this.bottomLeft.y += y;

    	this.bottomRight.x += x;
    	this.bottomRight.y += y;

    	this.center.x += x;
    	this.center.y += y;
    }
    
    public void offset(Point pt) {
    	offset(pt.x, pt.y);
    }

    public void divideCoordinatesBy(int i) {
        this.scaleFactor=i;

        this.topLeft.x = this.topLeft.x/i;
        this.topLeft.y = this.topLeft.y/i;

        this.topRight.x = this.topRight.x/i;
        this.topRight.y = this.topRight.y/i;

        this.bottomLeft.x = this.bottomLeft.x/i;
        this.bottomLeft.y = this.bottomLeft.y/i;

        this.bottomRight.x = this.bottomRight.x/i;
        this.bottomRight.y = this.bottomRight.y/i;

        this.center.x = this.center.x/i;
        this.center.y = this.center.y/i;
    }
    
    public int getX() {
    	return this.topLeft.x;
    }
    
    public int getY() {
    	return this.topLeft.y;
    }
    
    public int getX2() {
    	return this.bottomRight.x;
    }
    
    public int getY2() {
    	return this.bottomRight.y;
    }
    
    public int getCX() {
    	return this.center.x;
    }
    
    public int getCY() {
    	return this.center.y;
    }

    public int getWidth(){
        return this.topRight.x-this.topLeft.x;
    }

    public int getHeight(){
        return this.bottomLeft.y-this.topLeft.y;
    }

    public int getScaleFactor(){
        return this.scaleFactor;
    }

    public void setResizeFactor(double resizeFactor) {
        this.resizeFactor=resizeFactor;
    }
    public double getResizeFactor(){
        return this.resizeFactor;
    }
    
    public void setLocation(ImageLocation l) {
    	this.topLeft.setLocation(l.getTopLeft());
    	this.topRight.setLocation(l.getTopRight());
    	this.bottomLeft.setLocation(l.getBottomLeft());
    	this.bottomRight.setLocation(l.getBottomRight());
    	this.center.setLocation(l.getCenter());
    	
    	this.scaleFactor = l.getScaleFactor();
    	this.resizeFactor = l.getResizeFactor();
    	
    	this.queryImage = l.getQueryImageRef();
    	this.queryImageBase64 = l.getQueryImageBase64();
    	this.shotImage = l.getShotImageRef();
    	this.shotImageBase64 = l.getShotImageBase64();
    	
    	this.checkMain = l.getCheckMain();
    }
    
    public Rectangle convertRect() {
        double x = getTopLeft().x;
        double y = getTopLeft().y;
        double width = getWidth();
        double height = getHeight();
        int scaleFactor = getScaleFactor();
        double resizeFactor = getResizeFactor();

        int x_resized = (int)x, y_resized = (int)y, 
        	width_resized = (int)width, height_resized = (int)height;
        if(resizeFactor != 1.0 || scaleFactor != 1.0) {
	        x_resized = (int) (x / resizeFactor)*scaleFactor;
	        y_resized = (int) (y / resizeFactor)*scaleFactor;
	        width_resized = (int) (width / resizeFactor)*scaleFactor;
	        height_resized = (int) (height / resizeFactor)*scaleFactor;
        }

        Rectangle croppedRect = new Rectangle(x_resized, y_resized, width_resized, height_resized);
        return croppedRect;
    }
    
    public void setMethod(String method) {
    	this.method = method;
    }
    
    public String getMethod() {
    	return this.method;
    }
    
    public boolean equalMethod(String method) {
    	if(method == null || this.method == null)
    		return false;
    	return this.method.compareToIgnoreCase(method) == 0;
    }
    
    public BufferedImage getQueryImage() {
    	if(this.queryImage != null && this.queryImage.get() != null)
    		return this.queryImage.get();
    	return null;
    }
    
    public SoftReference<BufferedImage> getQueryImageRef() {
    	return this.queryImage;
    }
   
    public String getQueryImageBase64() {
    	return this.queryImageBase64;
    }
    
    public void setQueryImage(SoftReference<BufferedImage> image) {
    	if(image != null && image.get() != null)
    		this.queryImage = ImageUtil.cloneImageRef(image.get());
    }
    
    public void encodeBase64QueryImage() {
    	if(this.queryImage != null && this.queryImage.get() != null)
    		this.queryImageBase64 = ImageUtil.encodeByBase64(this.queryImage.get());
    }
    
    public BufferedImage getShotImage() {
    	if(this.shotImage != null && this.shotImage.get() != null)
    		return this.shotImage.get();
    	return null;
    }
    
    public SoftReference<BufferedImage> getShotImageRef() {
    	return this.shotImage;
    }
    
    public String getShotImageBase64() {
    	return this.shotImageBase64;
    }
    
    public void setShotImage(SoftReference<BufferedImage> image) {
    	if(image != null && image.get() != null)
    		this.shotImage = ImageUtil.cloneImageRef(image.get());
    }
    
    public void encodeBase64ShotImage() {
    	if(this.shotImage != null && this.shotImage.get() != null)
    		this.shotImageBase64 = ImageUtil.encodeByBase64(this.shotImage.get());
    }
    
    public void encodeBase64Image() {
    	encodeBase64QueryImage();
    	encodeBase64ShotImage();
    }
    
    public void clearImage() {
    	if(this.queryImage != null && this.queryImage.get() != null)
    		this.queryImage.clear();
    	if(this.shotImage != null && this.shotImage.get() != null)
    		this.shotImage.clear();
    }
    
    public void setCheckMain(int code) {
    	this.checkMain = code;
    }
    
    public int getCheckMain() {
    	return this.checkMain;
    }
    
    
}
