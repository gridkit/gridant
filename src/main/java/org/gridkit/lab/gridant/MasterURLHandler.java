package org.gridkit.lab.gridant;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.apache.tools.ant.Project;

class MasterURLHandler extends URLStreamHandler {

	private final MasterExecutor executor;

	public MasterURLHandler(MasterExecutor executor) {
		this.executor = executor;
	}

	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		byte[] data;
		try {
			final String path = u.toURI().toString();
			data = executor.exec(new MasterCallable<byte[]>() {

				@Override
				public byte[] call(Project project) throws Exception {
					URL u = new URI(path).toURL();				
					return readData(u);
				}
				
			});
			return new BytesURLConnection(u, data);
		} catch (UndeclaredThrowableException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException)e.getCause();
			}
			else {
				throw e;
			}
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		
	}

	private static byte[] readData(URL url) {
		try {
			InputStream fis = url.openStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[4 << 20];
			while(true) {
				int m = fis.read(buf);
				if (m < 0) {
					break;
				}
				bos.write(buf, 0, m);
			}
			fis.close();
			return bos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} 
	}

	private static class BytesURLConnection extends URLConnection {

		protected byte[] content;

		public BytesURLConnection(URL url, byte[] content) {
			super(url);
			this.content = content;
		}

		public void connect() {
		}

		public InputStream getInputStream() {
			return new ByteArrayInputStream(content);
		}
	}
}
