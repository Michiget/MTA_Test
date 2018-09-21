package com.hyundai.autoever.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

public class CompressionUtil {
	private static boolean debug = false;
	
	/**
	 * 압축파일이 존재하는 디렉토리에 압축 해제
	 * 
	 * @param zippedFile
	 * @throws IOException
	 */
	public static void unzip(File zippedFile) throws IOException {
		unzip(zippedFile, Charset.defaultCharset().name());
	}
	
	public static void unzip(File zippedFile, String encoding ) throws IOException {
		unzip(zippedFile, zippedFile.getParentFile(), encoding);
	}
	
	public static void unzip(File zippedFile, File destDir) throws IOException {
		unzip(new FileInputStream(zippedFile), destDir, Charset.defaultCharset().name());
	}
	
	public static void unzip(File zippedFile, File destDir, String encoding)
	throws IOException {
		unzip(new FileInputStream(zippedFile), destDir, encoding);
	}
	
	public static void unzip(InputStream is, File destDir) throws IOException{
		unzip(is, destDir, Charset.defaultCharset().name());
	}
	
	public static void unzip( InputStream is, File destDir, String encoding)
	throws IOException {
		ZipArchiveInputStream zis ;
		ZipArchiveEntry entry ;
		String name ;
		File target ;
		int nWritten = 0;
		BufferedOutputStream bos ;
		byte [] buf = new byte[1024 * 8];

		ensureDestDir(destDir);
		
		zis = new ZipArchiveInputStream(is, encoding, false);
		while ( (entry = zis.getNextZipEntry()) != null ){
			name = entry.getName();
			target = new File (destDir, name);
			if ( entry.isDirectory() ){
				ensureDestDir(target);
			} else {
				target.createNewFile();
				bos = new BufferedOutputStream(new FileOutputStream(target));
				while ((nWritten = zis.read(buf)) >= 0 ){
					bos.write(buf, 0, nWritten);
				}
				bos.close();
				debug ("file : " + name);
			}
		}
		zis.close();
	}
	
	public static boolean unzipFile(File zippedFile, String source) throws IOException {
		return unzipFile(zippedFile, Charset.defaultCharset().name(), source);
	}
	
	public static boolean unzipFile(File zippedFile, String encoding, String source) throws IOException {
		return unzipFile(zippedFile, zippedFile.getParentFile(), encoding, source);
	}
	
	public static boolean unzipFile(File zippedFile, File destDir, String source) throws IOException {
		return unzipFile(new FileInputStream(zippedFile), destDir, Charset.defaultCharset().name(), source);
	}
	
	public static boolean unzipFile(File zippedFile, File destDir, String encoding, String source)
	throws IOException {
		return unzipFile(new FileInputStream(zippedFile), destDir, encoding, source);
	}
	
	public static boolean unzipFile(InputStream is, File destDir, String source) throws IOException{
		return unzipFile(is, destDir, Charset.defaultCharset().name(), source);
	}
	
	public static boolean unzipFile( InputStream is, File destDir, String encoding, String source)
	throws IOException {
		ZipArchiveInputStream zis ;
		ZipArchiveEntry entry ;
		String name, fileName ;
		File target ;
		int nWritten = 0;
		boolean ok = false;
		BufferedOutputStream bos ;
		byte [] buf = new byte[1024 * 8];
		
		fileName = source;
		int n = source.lastIndexOf('/');
		if(n >= 0)
			fileName = source.substring(n+1);

		ensureDestDir(destDir);
		
		zis = new ZipArchiveInputStream(is, encoding, false);
		while ( (entry = zis.getNextZipEntry()) != null ){
			name = entry.getName();
			if(name.equals(source) && !entry.isDirectory()) {
				target = new File (destDir, fileName);
				target.createNewFile();
				bos = new BufferedOutputStream(new FileOutputStream(target));
				while ((nWritten = zis.read(buf)) >= 0 ){
					bos.write(buf, 0, nWritten);
				}
				bos.close();
				ok = true;
				debug ("file : " + fileName);
				break;
			}
		}
		zis.close();
		return ok;
	}
	
	private static void ensureDestDir(File dir) throws IOException {
		
		if ( ! dir.exists() ) {
			dir.mkdirs(); /*  does it always work? */
			debug ("dir  : " + dir);
		}
		
	}
	
	/**
	 * compresses the given file(or dir) and creates new file under the same directory.
	 * @param src file or directory
	 * @throws IOException
	 */
	public static void zip(File src) throws IOException{
		zip(src, Charset.defaultCharset().name(), true);
	}
	/**
	 * zips the given file(or dir) and create 
	 * @param src file or directory to compress
	 * @param includeSrc if true and src is directory, then src is not included in the compression. if false, src is included.
	 * @throws IOException
	 */
	public static void zip(File src, boolean includeSrc) throws IOException{
		zip(src, Charset.defaultCharset().name(), includeSrc);
	}
	/**
	 * compresses the given src file (or directory) with the given encoding
	 * @param src
	 * @param charSetName
	 * @param includeSrc
	 * @throws IOException
	 */
	public static void zip(File src, String charSetName, boolean includeSrc) throws IOException {
		zip( src, src.getParentFile(), charSetName, includeSrc);
	}
	/**
	 * compresses the given src file(or directory) and writes to the given output stream.
	 * @param src
	 * @param os
	 * @throws IOException
	 */
	public static void zip(File src, OutputStream os) throws IOException {
		zip(src, os, Charset.defaultCharset().name(), true);
	}
	/**
	 * compresses the given src file(or directory) and create the compressed file under the given destDir. 
	 * @param src
	 * @param destDir
	 * @param charSetName
	 * @param includeSrc
	 * @throws IOException
	 */
	public static void zip(File src, File destDir, String charSetName, boolean includeSrc) throws IOException {
		String fileName = src.getName();
		if ( !src.isDirectory() ){
			int pos = fileName.lastIndexOf(".");
			if ( pos >  0){
				fileName = fileName.substring(0, pos);
			}
		}
		fileName += ".zip";
		ensureDestDir(destDir);
		
		File zippedFile = new File ( destDir, fileName);
		if ( !zippedFile.exists() ) zippedFile.createNewFile();
		zip(src, new FileOutputStream(zippedFile), charSetName, includeSrc);
	}
	
	public static void zip(File [] filesToZip, OutputStream os, String encoding ) {
		
	}
	
	public static void zip(File src, OutputStream os, String charsetName, boolean includeSrc)
	throws IOException {
		ZipArchiveOutputStream zos = new ZipArchiveOutputStream(os);
		zos.setEncoding(charsetName);
		FileInputStream fis ;
		
		int length ;
		ZipArchiveEntry ze ;
		byte [] buf = new byte[8 * 1024];
		String name ;
		
		Stack<File> stack = new Stack<File>();
		File root ;
		if ( src.isDirectory() ) {
			if( includeSrc ){
				stack.push(src);
				root = src.getParentFile();
			}
			else {
				File [] fs = src.listFiles();
				for (int i = 0; i < fs.length; i++) {
					stack.push(fs[i]);
				}
				root = src;
			}
		} else {
			stack.push(src);
			root = src.getParentFile();
		}
		
		while ( !stack.isEmpty() ){
			File f = stack.pop();
			name = toPath(root, f);
			if ( f.isDirectory()){
				debug ("dir  : " + name);
				File [] fs = f.listFiles();
				for (int i = 0; i < fs.length; i++) {
					if ( fs[i].isDirectory() ) stack.push(fs[i]);
					else stack.add(0, fs[i]);
				}
			} else {
				debug("file : " + name);
				ze = new ZipArchiveEntry(name);
				zos.putArchiveEntry(ze);
				fis = new FileInputStream(f);
				while ( (length = fis.read(buf, 0, buf.length)) >= 0 ){
					zos.write(buf, 0, length);
				}
				fis.close();
				zos.closeArchiveEntry();
			}
		}
		zos.close();
	}
	
	// 리스트로 파일을 받아 zip로 압축
	public static void zip(List<File> src, OutputStream os) throws IOException {
		ZipArchiveOutputStream zos = new ZipArchiveOutputStream(os);
		zos.setEncoding(Charset.defaultCharset().name());
		FileInputStream fis = null;

		int length;
		ZipArchiveEntry ze;
		byte[] buf = new byte[8 * 1024];

		if (src.size() > 0) {

			for (int i = 0; i < src.size(); i++) {
				System.out.println("name: " + src.get(i).getName());
				ze = new ZipArchiveEntry(src.get(i).getName());
				zos.putArchiveEntry(ze);
				fis = new FileInputStream(src.get(i));
				while ((length = fis.read(buf, 0, buf.length)) >= 0) {
					zos.write(buf, 0, length);
				}
			}

			fis.close();
			zos.closeArchiveEntry();
		}

		zos.close();
	}
	
	private static String toPath(File root, File dir){
		String path = dir.getAbsolutePath();
		path = path.substring(root.getAbsolutePath().length()).replace(File.separatorChar, '/');
		if ( path.startsWith("/")) path = path.substring(1);
		if ( dir.isDirectory() && !path.endsWith("/")) path += "/" ;
		return path ;
	}
	private static void debug(String msg){
		if( debug ) System.out.println(msg);
	}
	
	public static void main(String[] args) {
		/*//폴더 압축
		try {
			CompressionUtil.zip( new File("c:/sharpplus/upload/20") );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		/*//파일 삭제
		File objFile = new File("c:/sharpplus/upload/20.zip");
		objFile.delete();
		*/
		//파일리스트로 압축
		try {
			List<File> filelist = new ArrayList<File>();
			filelist.add(new File("c:/file/upload/20/20_1409205498748_첨삭완료.docx"));
			filelist.add(new File("c:/file/upload/20/20_1409205504328_첨삭완료.docx"));
			
			File zippedFile = new File ( "c:/file/upload/20", "1234.zip");
			CompressionUtil.zip(filelist, new FileOutputStream(zippedFile));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}