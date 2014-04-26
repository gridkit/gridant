package org.gridkit.lab.gridant;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;

import junit.framework.Assert;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.launch.LaunchException;
import org.junit.Assume;
import org.junit.Test;

public class SyncDownRunTest {

    
    
	@Test
	public void run_sync() throws MalformedURLException, LaunchException {
	    deleteAll(new File("target/sync-test"));
		runLocalTarget("simple-sync");
	}

	@Test
	public void run_sync2and3() throws MalformedURLException, LaunchException {
	    deleteAll(new File("target/sync-test"));
	    runLocalTarget("simple-sync2");
	    runLocalTarget("simple-sync3");
	}

	@Test
	public void run_remote_sync() throws MalformedURLException, LaunchException {
	    assumeHost("cbox1");
	    runRemoteTarget("simple-sync");
	}

	private void assumeHost(String hostname) {
		try {
			Socket soc = new Socket();
			soc.connect(new InetSocketAddress(hostname, 22), 1000);
			soc.close();
		} catch(IOException e) {
			System.err.println("Host verification failed [" + hostname + "] " + e.toString());
			Assume.assumeTrue(false);
		}
	}
	
	private void deleteAll(File path) {
		File[] clist = path.listFiles();
		if (clist != null) {
			for(File c : clist) {
				if (c.isDirectory()) {
					deleteAll(c);
				}
				else {
					c.delete();
				}
			}
		}
		path.delete();
	}
		
	private void runLocalTarget(String target) throws MalformedURLException, LaunchException {
		errorMessage = null;		
		System.setProperty("basedir", "src/test/resources");
		Assert.assertEquals(0, MockLauncher.main("-listener", Listener.class.getName(), "-buildfile", "src/test/resources/sync-down-test-script.xml", target));	
		if (errorMessage != null) {
			Assert.fail(errorMessage);
		}
	}	

	private void runRemoteTarget(String target) throws MalformedURLException, LaunchException {
		errorMessage = null;
		System.setProperty("basedir", "src/test/resources");
		Assert.assertEquals(0, MockLauncher.main("-listener", Listener.class.getName(), "-buildfile", "src/test/resources/remote-sync-down-test-script.xml", target));
		if (errorMessage != null) {
			Assert.fail(errorMessage);
		}
	}	

	static String errorMessage; 
	
	public static class Listener implements BuildListener {

		
		@Override
		public void buildStarted(BuildEvent event) {
			// ignore
		}

		@Override
		public void buildFinished(BuildEvent event) {
			if (event.getException() != null) {
				errorMessage = event.getException().toString();
			}
		}

		@Override
		public void targetStarted(BuildEvent event) {
			// ignore
		}

		@Override
		public void targetFinished(BuildEvent event) {
			// ignore
		}

		@Override
		public void taskStarted(BuildEvent event) {
			// ignore
		}

		@Override
		public void taskFinished(BuildEvent event) {
			// ignore
		}

		@Override
		public void messageLogged(BuildEvent event) {
			// ignore
		}
	}
}
