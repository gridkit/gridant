package org.gridkit.lab.gridant;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import junit.framework.Assert;

import org.junit.Test;

public class UrlHandlerTest {

	@Test
	public void verify_original_url() throws MalformedURLException {
		URL url = new File("src/test/resources/marker.txt").toURI().toURL();
		Assert.assertEquals("marker", readData(url));
	}

	@Test
	public void verify_rebuild_url() throws MalformedURLException {
		URL url = new File("src/test/resources/marker.txt").toURI().toURL();
		URL rurl = new URL(url, url.getFile(), null);
		
		Assert.assertEquals("marker", readData(rurl));
	}

	@Test
	public void verify_rebuild_absolute_url() throws MalformedURLException {
		URL url = new File("src/test/resources/marker.txt").getAbsoluteFile().toURI().toURL();
		URL rurl = new URL(url, url.getFile(), null);
		
		Assert.assertEquals("marker", readData(rurl));
	}

	@Test
	public void verify_mocked_rebuild_url() throws MalformedURLException {
		URL url = new File("src/test/resources/marker.txt").toURI().toURL();
		URL rurl = new URL(url, url.getFile(), new MasterURLHandler(mockExecutor("mock")));
		
		Assert.assertEquals("mock", readData(rurl));
	}

	@Test
	public void verify_mocked_rebuild_absolute_url() throws MalformedURLException {
		URL url = new File("src/test/resources/marker.txt").getAbsoluteFile().toURI().toURL();
		URL rurl = new URL(url, url.getFile(), new MasterURLHandler(mockExecutor("mock")));
		
		Assert.assertEquals("mock", readData(rurl));
	}
	
	private static MasterExecutor mockExecutor(final String text) {
		return new MasterExecutor() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T exec(MasterCallable<T> task) {
				return (T) text.getBytes();
			}
		};
	}
	
	private static String readData(URL url) {
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
			return new String(bos.toByteArray());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} 
	}	
}
