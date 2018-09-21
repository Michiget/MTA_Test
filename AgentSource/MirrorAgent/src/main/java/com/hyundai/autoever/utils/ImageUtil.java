package com.hyundai.autoever.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import org.sikuli.script.RunTime;

import com.google.common.io.BaseEncoding;
import com.hyundai.autoever.mirror.engine.ServerModule;

public class ImageUtil {

	  static {
		  if(ServerModule.get().isUseOpengl()) {
			  //AnsiLog.d("Core.NATIVE_LIBRARY_NAME : " + Core.NATIVE_LIBRARY_NAME);
			  //RunTime.loadLibrary("libopencv_java248");
			  try {
				  RunTime.loadLibrary("lib" + Core.NATIVE_LIBRARY_NAME);
			  } catch(Exception e) {
				  e.printStackTrace();
			  }
		  }
	  }

	public static BufferedImage createImageFromByte(byte[] imageInByte) {
		BufferedImage bufferedImage = null;
		InputStream in = new ByteArrayInputStream(imageInByte);
		try {
			bufferedImage = ImageIO.read(in);
			if (bufferedImage == null) {
				AnsiLog.e(8001, " " + "Failed to load a frame image.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return bufferedImage;
	}

	public static SoftReference<BufferedImage> createImageFromByteRef(byte[] imageInByte) {
		BufferedImage bufferedImage = createImageFromByte(imageInByte);
		SoftReference<BufferedImage> bufferedImageRef = null;
		if(bufferedImage != null)
			bufferedImageRef = new SoftReference<BufferedImage>(bufferedImage);
		return bufferedImageRef;
	}
	
	public static byte[] createByteFromImage(BufferedImage originalImage) {
		byte[] imageInByte = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(originalImage, "jpg", baos);
			baos.flush();
			imageInByte = baos.toByteArray();
			baos.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		return imageInByte;
	}
	
	public static SoftReference<byte[]> createByteFromImageRef(BufferedImage originalImage) {
		SoftReference<byte[]> imageInByte = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(originalImage, "jpg", baos);
			baos.flush();
			imageInByte = new SoftReference<byte[]>(baos.toByteArray());
			baos.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		return imageInByte;
	}
	
	public static double opencv_compareImage(BufferedImage img1, BufferedImage img2, double rate) {
		if(img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight())
			return rate*0.9f;

		double res = 0;
		Mat mat_1 = opencv_toMat(img1);
		Mat mat_2 = opencv_toMat(img2);
		
		Mat hist_1 = new Mat();
		Mat hist_2 = new Mat();
		
		Mat mask_1 = new Mat();
		Mat mask_2 = new Mat();
		
		MatOfInt channel_1 = new MatOfInt(0);
		MatOfInt channel_2 = new MatOfInt(0);
		
		MatOfFloat ranges = new MatOfFloat(0f,256f);
		MatOfInt histSize = new MatOfInt(25);
		
		Imgproc.calcHist(Arrays.asList(mat_1), channel_1, mask_1, hist_1, histSize, ranges);
		Imgproc.calcHist(Arrays.asList(mat_2), channel_2, mask_2, hist_2, histSize, ranges);
	    
		res = Imgproc.compareHist(hist_1, hist_2, Imgproc.CV_COMP_CORREL) * rate;
		
		mat_1 = mat_2 = null;
		hist_1 = hist_2 = mask_1 = mask_2 = null;
		channel_1 = channel_2 = null;
		ranges = null;
		histSize = null;
		return res;
	}
	
	public static boolean opencv_compareChangeImage(BufferedImage img1, BufferedImage img2, double rateMax, double limitMax) {
		boolean change = false;
		
		if(img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight())
			return true;
		
		SoftReference<Mat> mat_1 = opencv_convMatRef(img1);
		SoftReference<Mat> mat_2 = opencv_convMatRef(img2);
		
		SoftReference<Mat> hist_1 = new SoftReference<Mat>(new Mat());
		SoftReference<Mat> hist_2 = new SoftReference<Mat>(new Mat());
		
		SoftReference<Mat> mask_1 = new SoftReference<Mat>(new Mat());
		SoftReference<Mat> mask_2 = new SoftReference<Mat>(new Mat());
		
		SoftReference<MatOfInt> channel_1 = new SoftReference<MatOfInt>(new MatOfInt(0));
		SoftReference<MatOfInt> channel_2 = new SoftReference<MatOfInt>(new MatOfInt(0));
		
		SoftReference<MatOfFloat> ranges = new SoftReference<MatOfFloat>(new MatOfFloat(0f,256f));
		SoftReference<MatOfInt> histSize = new SoftReference<MatOfInt>(new MatOfInt(25));
		
		double res = 0;
		
		Imgproc.calcHist(Arrays.asList(mat_1.get()), channel_1.get(), mask_1.get(), hist_1.get(), histSize.get(), ranges.get());
		Imgproc.calcHist(Arrays.asList(mat_2.get()), channel_2.get(), mask_2.get(), hist_2.get(), histSize.get(), ranges.get());
	    
		res = Imgproc.compareHist(hist_1.get(), hist_2.get(), Imgproc.CV_COMP_CORREL) * rateMax;
		
		if(res < limitMax) {
			change = true;
		}
		
		mat_1.clear();
		mat_2.clear();
		hist_1.clear();
		hist_2.clear();
		mask_1.clear();
		mask_2.clear();
		channel_1.clear();
		channel_2.clear();
		ranges.clear();
		histSize.clear();
		return change;
	}
	
	public static SoftReference<byte[]> opencv_resizeByteFromImage(BufferedImage originalImage, int w, int h) {
		SoftReference<byte[]> imageInByte = null;
		if(originalImage == null)
			return imageInByte;
		
		try {
			int iw = originalImage.getWidth(), ih = originalImage.getHeight();
			DataBufferByte dbb = (DataBufferByte)originalImage.getRaster().getDataBuffer();
			byte[] pixels = dbb.getData();
			SoftReference<Mat> resizeimage = null, matImg = null;
			SoftReference<MatOfByte> mob = null;
			
			matImg = new SoftReference<Mat>(new Mat(ih, iw, CvType.CV_8UC3));
			matImg.get().put(0, 0, pixels);
			
			if(w != iw || h != ih) {
				Size sz = new Size(w, h);
				resizeimage = new SoftReference<Mat>(new Mat());
				Imgproc.resize(matImg.get(), resizeimage.get(), sz);
				matImg.clear();
			}
			else {
				resizeimage = matImg;
			}
			
			mob = new SoftReference<MatOfByte>(new MatOfByte());
			Highgui.imencode(".jpg", resizeimage.get(), mob.get());
			
			imageInByte = new SoftReference<byte[]>(mob.get().toArray());
			mob.clear();
			resizeimage.clear();
		} catch(Exception e) {
			e.printStackTrace();
		}
		return imageInByte;
	}
	
	public static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
		BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = resizedImage.createGraphics();
//		if(width < 200)
//			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
//		else
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(originalImage, 0, 0, width, height, null);
		g.dispose();
		return resizedImage;
	}
	
	public static SoftReference<BufferedImage> resizeImageRef(BufferedImage originalImage, int width, int height) {
		BufferedImage resizedImage = resizeImage(originalImage, width, height);
		SoftReference<BufferedImage> resizedImageRef = null;
		if(resizedImage != null)
			resizedImageRef = new SoftReference<BufferedImage>(resizedImage);
		return resizedImageRef;
	}

	public static BufferedImage cloneImage(BufferedImage image) {
		if(image == null)
			return null;
		 
		BufferedImage cloneImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = cloneImage.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
	
//		BufferedImage cloneImage = null;
//		ColorModel cm = image.getColorModel();
//		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
//		WritableRaster raster = image.copyData(null);
//		BufferedImage rasterImage = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
//		if(rasterImage.getWidth() > image.getWidth() || rasterImage.getHeight() > image.getHeight())
//			cloneImage = rasterImage.getSubimage(0, 0, image.getWidth(), image.getHeight());
//		else
//			cloneImage = rasterImage;

//		BufferedImage cloneImage = null;
//		try {
//			cloneImage = image.getSubimage(0, 0, image.getWidth(), image.getHeight());
//		} catch(Exception e) {
//			e.printStackTrace();
//		}
		return cloneImage;
	}

	public static SoftReference<BufferedImage> cloneImageRef(BufferedImage image) {
		if(image == null)
			return null;
		
		BufferedImage cloneImage = cloneImage(image);
		SoftReference<BufferedImage> cloneImageRef = null;
		if(cloneImage != null)
			cloneImageRef = new SoftReference<BufferedImage>(cloneImage);
		return cloneImageRef;
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
		
		if(x < 0 || y < 0 || crw <= 0 || crh <= 0)
			return null;
		
		BufferedImage cropImage = null;
		try {
			cropImage = image.getSubimage(x, y, crw, crh);
		} catch(Exception e) {
			e.printStackTrace();
		}
		return cropImage;
	}

	public static SoftReference<BufferedImage> cropImageRef(BufferedImage image, int x, int y, int crw, int crh) {
		if(image == null)
			return null;
		
		BufferedImage cropImage = cropImage(image, x, y, crw, crh);
		SoftReference<BufferedImage> cropImageRef = null;
		if(cropImage != null)
			cropImageRef = new SoftReference<BufferedImage>(cropImage);
		return cropImageRef;
	}

	public static SoftReference<BufferedImage> cropImageRefExceptBlack(BufferedImage image, int min, int max, boolean landscape) {
		if(image == null)
			return null;
		
		boolean org_landscape = landscape;
		int w = image.getWidth();
		int h = image.getHeight();
		int cnt = 5;
		int s = (max-min)/cnt;
		int[] p = new int[cnt];
		int i, pixel, cb, cg, cr, crw = -1, crh = -1;
		
		for(i=0;i<p.length;i++) {
			p[i] = (max-1) - (s*i);
		}
		
		if(landscape) {
			for(i=0;i<p.length;i++) {
				for(int r=h-1;r>=0;r--) {
					pixel = image.getRGB(p[i], r);
					cb = pixel & 0xff;
					cg = (pixel & 0xff00) >> 8;
					cr = (pixel & 0xff0000) >> 16;
					if(cb > 5 || cg > 5 || cr > 5) {
						crh = Math.max(crh, r);
						break;
					}
				}
			}
			if(crh < 0) {
				crh = max;
				crw = min;
			}
			else {
				if(crh < min) {
					landscape = !landscape;
				}
				else {
					crh = min;
					crw = max;
				}
			}
		}

		if(!landscape) {
			for(i=0;i<p.length;i++) {
				for(int c=w-1;c>=0;c--) {
					pixel = image.getRGB(c, p[i]);
					cb = pixel & 0xff;
					cg = (pixel & 0xff00) >> 8;
					cr = (pixel & 0xff0000) >> 16;
					if(cb > 5 || cg > 5 || cr > 5) {
						crw = Math.max(crw, c);
						break;
					}
				}
			}
			if(crw < 0 && org_landscape) {
				crw = max;
				crh = min;
			}
			else {
				crw = min;
				crh = max;
			}
		}
		
		SoftReference<BufferedImage> cropImage = null;
		try {
			cropImage = new SoftReference<BufferedImage>(image.getSubimage(0, 0, crw, crh));
		} catch(Exception e) {
			e.printStackTrace();
		}
		return cropImage;
	}
	
	public static BufferedImage toBufferedImage(java.awt.Image img) {
        return toBufferedImage(img, 1.0);
    }
	
	public static BufferedImage toBufferedImage(java.awt.Image img, double resizeFactor) {
        if (resizeFactor <= 1.0 && img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        int w, h, sw, sh;
        sw = w = img.getWidth(null); sh = h = img.getHeight(null);
        if(resizeFactor > 1.0) {
        	sw = (int)((double)w/resizeFactor); sh = (int)((double)h/resizeFactor);
        }
        BufferedImage bimage = new BufferedImage(sw, sh, BufferedImage.TYPE_3BYTE_BGR);

        Graphics2D bGr = bimage.createGraphics();
        if(resizeFactor > 1.0)
        	bGr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        bGr.drawImage(img, 0, 0, sw, sh, null);
        bGr.dispose();

        return bimage;
    }
		
	public static String encodeByBase64(BufferedImage image) {
		String base64 = null;
    	if(image != null) {
    		byte[] binary = createByteFromImage(image);
    		if(binary != null) {
    			try {
    				base64 = BaseEncoding.base64().encode(binary);
    			} catch(Exception e) {}
    			binary = null;
    		}
    	}
    	return base64;
    }
	
	public static BufferedImage decodeByBase64(String base64) {
		BufferedImage image = null;
    	if(base64 != null) {
    		try {
	    		byte[] binary = BaseEncoding.base64().decode(base64);
	    		if(binary != null) {
	    			image = createImageFromByte(binary);
	    		}
    		} catch(Exception e) {}
    	}
    	return image;
    }
	
	public static SoftReference<BufferedImage> decodeByBase64Ref(String base64) {
		SoftReference<BufferedImage> image = null;
    	if(base64 != null) {
    		try {
	    		byte[] binary = BaseEncoding.base64().decode(base64);
	    		if(binary != null) {
	    			image = createImageFromByteRef(binary);
	    		}
    		} catch(Exception e) {}
    	}
    	return image;
    }
	
	// if rate is 100, it's 100%.
	public static double compareByteArrays(byte[] a, byte[] b, double rate) {
		int n = Math.min(a.length, b.length), nLarge = Math.max(a.length, b.length);
		int unequalCount = nLarge - n;
		for (int i=0; i<n; i++) 
		    if (a[i] != b[i]) unequalCount++;
		return unequalCount * rate / nLarge;
	}
	
	private static Mat opencv_toMat(BufferedImage img) {
		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		Mat mat = new Mat(img.getHeight(),img.getWidth(),CvType.CV_8UC3);
		mat.put(0,0,data);
		
		return mat;
	}
	
	private static SoftReference<Mat> opencv_toMatRef(BufferedImage img) {
		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		SoftReference<Mat> mat = new SoftReference<Mat>(new Mat(img.getHeight(),img.getWidth(),CvType.CV_8UC3));
		mat.get().put(0,0,data);
		return mat;
	}
	
	private static Mat opencv_convMat(BufferedImage img) {
		Mat mat = opencv_toMat(img);
		Mat mat1 = new Mat(img.getHeight(),img.getWidth(),CvType.CV_8UC3);
		Imgproc.cvtColor(mat, mat1, Imgproc.COLOR_RGB2HSV);
		
		return mat1;
	}
	
	private static SoftReference<Mat> opencv_convMatRef(BufferedImage img) {
		SoftReference<Mat> mat = opencv_toMatRef(img);
		SoftReference<Mat> mat1 = new SoftReference<Mat>(new Mat(img.getHeight(),img.getWidth(),CvType.CV_8UC3));
		Imgproc.cvtColor(mat.get(), mat1.get(), Imgproc.COLOR_RGB2HSV);
		mat.clear();
		return mat1;
	}
	
	private static Mat opencv_cropMat(BufferedImage img, int x, int y, int w, int h) {
		Rect roi = new Rect(x, y, w, h);
		Mat mat = opencv_toMat(img);
		return mat.submat(roi);
	}
	
	private static SoftReference<Mat> opencv_cropMatRef(BufferedImage img, int x, int y, int w, int h) {
		Rect roi = new Rect(x, y, w, h);
		SoftReference<Mat> mat = opencv_toMatRef(img);
		SoftReference<Mat> matsub = new SoftReference<Mat>(mat.get().submat(roi));
		mat.clear();
		return matsub;
	}
}
