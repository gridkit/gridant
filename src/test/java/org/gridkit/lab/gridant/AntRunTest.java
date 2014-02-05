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

public class AntRunTest {

	@Test
	public void run_echo() throws MalformedURLException, LaunchException {
		runLocalTarget("grid-echo");
	}

	@Test
	public void run_touch() throws MalformedURLException, LaunchException {
		deleteAll(new File("target/base1")); 
		deleteAll(new File("target/base2")); 
		runLocalTarget("grid-touch");
		Assert.assertTrue(new File("target/base1/server1.txt").exists());
		Assert.assertTrue(new File("target/base2/server2.txt").exists());
	}
	
//	@Test
//	public void run_touch2() throws MalformedURLException, LaunchException {
//		deleteAll(new File("target/base1")); 
//		deleteAll(new File("target/base2")); 
//		runLocalTarget("grid-touch2");
//		Assert.assertTrue(new File("target/base1/server1-2.txt").exists());
//		Assert.assertTrue(new File("target/base2/server2-2.txt").exists());
//	}

	@Test
	public void run_touch3() throws MalformedURLException, LaunchException {
		deleteAll(new File("target/base1")); 
		deleteAll(new File("target/base2")); 
		runLocalTarget("grid-touch3");
		Assert.assertTrue(new File("target/base1/server1-3.txt").exists());
		Assert.assertTrue(new File("target/base2/server2-3.txt").exists());
	}

	@Test
	public void run_remote_echo1() throws MalformedURLException, LaunchException {
		assumeHost("cbox1");
		assumeHost("cbox2");
		runRemoteTarget("cbox-grid-echo");
	}

	@Test
	public void run_remote_echo2() throws MalformedURLException, LaunchException {
		assumeHost("fbox");
		runRemoteTarget("fbox-grid-echo");
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
		Assert.assertEquals(0, MockLauncher.main("-buildfile", "src/test/resources/local-test-script.xml", target));	
		if (errorMessage != null) {
			Assert.fail(errorMessage);
		}
	}	

	private void runRemoteTarget(String target) throws MalformedURLException, LaunchException {
		errorMessage = null;
		Assert.assertEquals(0, MockLauncher.main("-listener", Listener.class.getName(), "-buildfile", "src/test/resources/remote-test-script.xml", target));
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
