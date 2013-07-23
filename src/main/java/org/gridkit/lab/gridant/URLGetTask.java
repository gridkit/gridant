package org.gridkit.lab.gridant;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class URLGetTask extends Task {

	private String url;
	private boolean useRelay;
	private String filename;
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public void setUseRelay(boolean useRelay) {
		this.useRelay = useRelay;
	}
	
	public void setFile(String file) {
		this.filename = file;
	}

	@Override
	public void execute() throws BuildException {
		try {
			System.out.println("Downloading: " + url);
			final URL u = new URL(url);
			if (filename == null) {
				filename = u.getPath();
				int c = filename.lastIndexOf('/');
				if (c >= 0) {
					filename = filename.substring(c + 1);
				}
			}
			FileOutputStream fos = new FileOutputStream(new File(filename));
			byte[] bytes;
			if (useRelay) {
				MasterExecutor mexec = getProject().getReference(GridAntRefs.MASTER_EXECUTOR);
				if (mexec == null) {
					throw new BuildException("Relay option can only be used for remote execution");
				}
				bytes = mexec.exec(new MasterCallable<byte[]>() {
					@Override
					public byte[] call(Project project) throws Exception {
						return urlToBytes(u);
					}
				});
			}
			else {
				bytes = urlToBytes(u);
			}
			
			fos.write(bytes);
			fos.close();
			System.out.println("" + bytes.length + " bytes written to " + filename);
		} catch (Exception e) {
			throw new BuildException(e);
		}
	}
	
	private static byte[] urlToBytes(URL url) throws IOException {
		return toBytes(url.openStream());
	}
	
	public static byte[] toBytes(InputStream is) throws IOException {
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] swap = new byte[1024];
			while(true) {
				int n = is.read(swap);
				if (n < 0) {
					break;
				}
				else {
					buf.write(swap, 0, n);
				}
			}
			return buf.toByteArray();
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
	
}
