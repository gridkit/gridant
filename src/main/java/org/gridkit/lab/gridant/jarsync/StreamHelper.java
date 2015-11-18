package org.gridkit.lab.gridant.jarsync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
class StreamHelper {
	
	@SuppressWarnings("resource")
    public static byte[] readFile(File file) {
		try {
			if (file.length() > 1 << 30) {
				throw new ArrayIndexOutOfBoundsException("File is too big");
			}
			byte[] data = new byte[(int)file.length()];
			FileInputStream fis = new FileInputStream(file);
			int n = 0;
			while(n < data.length) {
				int m = fis.read(data, n, data.length - n);
				if (m < 0) {
				    close(fis);
					throw new RuntimeException("Cannot read file: " + file.getCanonicalPath());
				}
				n += m;
			}
			close(fis);
			return data;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} 
	}

    public static String digest(byte[] data, String algorithm) {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm);
			byte[] digest = md.digest(data);
			StringBuilder buf = new StringBuilder();
			for(byte b: digest) {
				buf.append(Integer.toHexString(0xF & (b >> 4)));
				buf.append(Integer.toHexString(0xF & (b)));
			}
			return buf.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	public static String toString(InputStream is) throws IOException {
		try {
			StringBuilder buf = new StringBuilder();
			Reader reader = new InputStreamReader(is);
			char[] swap = new char[1024];
			while(true) {
				int n = reader.read(swap);
				if (n < 0) {
					break;
				}
				else {
					buf.append(swap, 0, n);
				}
			}
			return buf.toString();
		}
		finally {
			try {
				is.close();
			}
			catch(Exception e) {
				// ignore
			}
		}
	}

	public static Collection<String> toLines(InputStream is) throws IOException {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			List<String> result = new ArrayList<String>();
			while(true) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				result.add(line);
			}
			return result;
		}
		finally {
			try {
				is.close();
			}
			catch(Exception e) {
				// ignore
			}
		}
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		try {
			byte[] buf = new byte[1 << 18];
			while(true) {
				int n = in.read(buf);
				if(n >= 0) {
					out.write(buf, 0, n);
				}
				else {
					break;
				}
			}
		} finally {
			try {
				in.close();
			}
			catch(Exception e) {
				// ignore
			}
		}
	}	

	public static void copyAvailable(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[in.available()];
		int n = in.read(buf);
		if(n >= 0) {
			out.write(buf, 0, n);
		}
	}

    private static void close(FileInputStream fis) {
        try {
            fis.close();
        } catch (IOException e) {
            // ignore
        }
    }	
}
