/**
 * 
 */
package com.hyundai.autoever.mirror.engine;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;

public interface AndroidScreenObserver {
	public void initWindow(Window window);
	public void initBanner(Banner banner);
	public void frameImage(SoftReference<BufferedImage> image);
	public void release();
}
