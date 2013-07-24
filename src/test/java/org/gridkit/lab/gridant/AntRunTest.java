package org.gridkit.lab.gridant;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;

import junit.framework.Assert;

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
	public void run_remote_echo() throws MalformedURLException, LaunchException {
		assumeHost("cbox1");
		assumeHost("cbox2");
		runRemoteTarget("grid-echo");
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
		Assert.assertEquals(0, MockLauncher.main("-buildfile", "src/test/resources/local-test-script.xml", target));	
	}	

	private void runRemoteTarget(String target) throws MalformedURLException, LaunchException {
		Assert.assertEquals(0, MockLauncher.main("-buildfile", "src/test/resources/remote-test-script.xml", target));	
	}	
}
