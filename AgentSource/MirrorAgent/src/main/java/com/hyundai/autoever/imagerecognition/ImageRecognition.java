package com.hyundai.autoever.imagerecognition;

import org.apache.log4j.Logger;
import org.opencv.core.Mat;
import org.sikuli.basics.Settings;
import org.sikuli.script.Finder;
import org.sikuli.script.Image;
import org.sikuli.script.ScreenImage;

import org.sikuli.script.Match;
import org.sikuli.script.Pattern;
import org.sikuli.script.Region;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class ImageRecognition {
	private static final Logger logger = Logger.getLogger(ImageRecognition.class);
	private static final double DEFAULT_MIN_SIMILARITY = 0.7;
	
	public static ImageLocation findImage(BufferedImage queryImage, BufferedImage sceneImage, double minSimilarityValue, int cx, int cy, double rw, double rh) {
		long end, start = System.currentTimeMillis();
		ImageLocation loc = null;
        Settings.MinSimilarity = minSimilarityValue;
        Finder f = null;
        SoftReference<BufferedImage> cropImage = null;
    	int x = 0, y = 0;
    	boolean debug = false;
		if(debug) {
			try {
				ImageIO.write(queryImage, "jpg", new File("findImage_query.jpg"));
				ImageIO.write(sceneImage, "jpg", new File("findImage_scene.jpg"));
			} catch (IOException e) {}
		}
		
        if(cx < 0 || cy < 0 || rw == 0 || rh == 0) {
        	f = new Finder(sceneImage);
        }
        else {
    		int dw, dh;
    		//int sw = sceneImage.getWidth(), sh = sceneImage.getHeight();
    		int crw = queryImage.getWidth(), crh = queryImage.getHeight();
    		crw *= rw; crh *= rh;
    		dw = crw/2; dw += crw%2;
    		dh = crh/2; dh += crh%2;
    		
    		x = cx-dw; if(x < 0) x = 0;
    		y = cy-dh; if(y < 0) y = 0;
    		
    		cropImage = cropImageRef(sceneImage, x, y, crw, crh);
    		if(cropImage != null) {
    			if(debug) {
    				try {
    					ImageIO.write(cropImage.get(), "jpg", new File("findImage_crop.jpg"));
    				} catch (IOException e) {}
    			}
    			
	    		crw = cropImage.get().getWidth();
	    		crh = cropImage.get().getHeight();
	    		
	        	f = new Finder(cropImage.get());
    		}
        }

        if(f != null) {
	        Image img = new Image(queryImage);
	        f.find(img);
	        if (f.hasNext()) {
	            Match m = f.next();
	            loc = new ImageLocation();
	            loc.setTopLeft(m.getTopLeft().getPoint());
	            loc.setTopRight(m.getTopRight().getPoint());
	            loc.setBottomLeft(m.getBottomLeft().getPoint());
	            loc.setBottomRight(m.getBottomRight().getPoint());
	            loc.setCenter(m.getCenter().getPoint());
	            
	            if(x > 0 || y > 0)
	            	loc.offset(x, y);
	        }
	        f.destroy();
        }
        
        if(cropImage != null)
        	cropImage.clear();
        
        end = System.currentTimeMillis();
        if(loc != null)
        	logger.info("findImage PT : " + loc.getCenter().toString() + ", time " + (end-start) + " miliseconds.");
        else
        	logger.info("findImage PT : NOT FOUND, time " + (end-start) + " miliseconds.");
        return loc;
    }
	
	public static ImageLocation findImage(BufferedImage queryImage, BufferedImage sceneImage, double minSimilarityValue) {
        return findImage(queryImage, sceneImage, minSimilarityValue, -1, -1, 0, 0);
    }
	
	public static ImageLocation findImage(BufferedImage queryImage, BufferedImage sceneImage) {
        return findImage(queryImage, sceneImage, DEFAULT_MIN_SIMILARITY);
    }
	
	public static ImageLocation findImage(BufferedImage queryImage, BufferedImage sceneImage, int cx, int cy, double rw, double rh) {
        return findImage(queryImage, sceneImage, DEFAULT_MIN_SIMILARITY, cx, cy, rw, rh);
    }
	
	public static List<ImageLocation> findImages(BufferedImage queryImage, BufferedImage sceneImage, double minSimilarityValue) {
		long end, start = System.currentTimeMillis();
		List<ImageLocation> locs = null;
        Settings.MinSimilarity = minSimilarityValue;
        Finder f = new Finder(sceneImage);

        f.findAll(new Image(queryImage));
        if (f.hasNext()) {
        	Match m;
        	ImageLocation loc;
        	locs = new ArrayList<ImageLocation>();
        	while(f.hasNext()) {
	            m = f.next();
	            loc = new ImageLocation();
	            loc.setTopLeft(m.getTopLeft().getPoint());
	            loc.setTopRight(m.getTopRight().getPoint());
	            loc.setBottomLeft(m.getBottomLeft().getPoint());
	            loc.setBottomRight(m.getBottomRight().getPoint());
	            loc.setCenter(m.getCenter().getPoint());
	            locs.add(loc);
        	}
        }
        f.destroy();
        end = System.currentTimeMillis();
        if(locs != null)
        	logger.info("findImage count : " + locs.size() + ", time " + (end-start) + " miliseconds.");
        else
        	logger.info("findImage count : 0, time " + (end-start) + " miliseconds.");
        logger.info("findImage time " + (end-start) + " miliseconds.");
        return locs;
    }
	
	public static List<ImageLocation> findImages(BufferedImage queryImage, BufferedImage sceneImage) {
        return findImages(queryImage, sceneImage, DEFAULT_MIN_SIMILARITY);
	}
	
	public static BufferedImage cropImage(BufferedImage image, int x, int y, int crw, int crh) {
		if(image == null)
			return null;
		
		int w = image.getWidth();
		int h = image.getHeight();
		
		if((x+crw) > w)
			crw = w-x;
		if((y+crh) > h)
			crh = h-y;
		
		if(x < 0 || y < 0 || crw < 0 || crh < 0)
			return null;
		
		BufferedImage cropImage = null;
		try {
			cropImage = image.getSubimage(x, y, crw, crh);
		} catch(Exception e) {
			e.printStackTrace();
		}
		return cropImage;
	}

	private static SoftReference<BufferedImage> cropImageRef(BufferedImage image, int x, int y, int crw, int crh) {
		if(image == null)
			return null;
		
		int w = image.getWidth();
		int h = image.getHeight();
		
		if((x+crw) > w)
			crw = w-x;
		if((y+crh) > h)
			crh = h-y;
		
		if(x < 0 || y < 0 || crw < 0 || crh < 0)
			return null;
		
		SoftReference<BufferedImage> cropImage = null;
		try {
			cropImage = new SoftReference<BufferedImage>(image.getSubimage(x, y, crw, crh));
		} catch(Exception e) {
			e.printStackTrace();
		}
		return cropImage;
	}

}
