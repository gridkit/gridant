package org.gridkit.lab.gridant.jarsync;

import static org.gridkit.lab.gridant.jarsync.TestHelper.methodName;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.gridkit.lab.gridant.jarsync.BatchCopyProcessor.CopyBatch;
import org.gridkit.lab.gridant.jarsync.BatchCopyProcessor.CopyReporter;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SimpleFileCopyProcessorTest {

	private static final long TESTID = System.currentTimeMillis();
	private static final String TARGET_PATH = "target/sfcp/" + TESTID;

	@BeforeClass
	public static void removeTargetDir() {
		TestHelper.rmrf("target/sfcp");
	}
	
	public FileSyncParty sync(String dst) {
	    return new SimpleSyncSlave(new File(dst));
	}
	
	@Test
	public void verify_simple_file_copy() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
	    String src = "src/test/resources/SimpleFileCopyProcessor/root1";
	    String dst = TARGET_PATH + "/" + methodName();
        CopyBatch batch = sfcp.startBatch(sync(src));
        CopyTracker tracker = new CopyTracker(src, dst);
        batch.sourceExclude("**/.mkdir");
        batch.sourceExclude("**/*.v2");        
        batch.copy("**");
        batch.prepare(tracker);
        batch.execute(sync(dst), tracker);
        
        StringBuilder expected = new StringBuilder();
        expected.append("pom.xml -> pom.xml <copy>").append("\n"); 
        expected.append("src/main/root/a.txt -> src/main/root/a.txt <copy>").append("\n"); 
        expected.append("src/main/root/b.txt -> src/main/root/b.txt <copy>").append("\n"); 
        expected.append("src/main/root/override1 -> src/main/root/override1 <dir>").append("\n"); 
        expected.append("src/main/root/override2 -> src/main/root/override2 <dir>").append("\n"); 
        expected.append("src/main/root/x.prop -> src/main/root/x.prop <copy>").append("\n"); 
        
        Assert.assertEquals(expected.toString(), tracker.toString());
	}

	@Test
	public void verify_simple_file_merge() throws IOException {
		SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
		String src = "src/test/resources/SimpleFileCopyProcessor/root1";
		String dst = TARGET_PATH + "/" + methodName();
		{
    		CopyBatch batch = sfcp.startBatch(sync(src));
    		CopyTracker tracker = new CopyTracker(src, dst);
    		batch.sourceExclude("**/.mkdir");
    		batch.sourceExclude("**/*.v2");
    		batch.copy("**");
            batch.prepare(tracker);
    		batch.execute(sync(dst), tracker);
    		
    		StringBuilder expected = new StringBuilder();
    		expected.append("pom.xml -> pom.xml <copy>").append("\n"); 
    		expected.append("src/main/root/a.txt -> src/main/root/a.txt <copy>").append("\n"); 
    		expected.append("src/main/root/b.txt -> src/main/root/b.txt <copy>").append("\n"); 
    		expected.append("src/main/root/override1 -> src/main/root/override1 <dir>").append("\n"); 
    		expected.append("src/main/root/override2 -> src/main/root/override2 <dir>").append("\n"); 
    		expected.append("src/main/root/x.prop -> src/main/root/x.prop <copy>").append("\n"); 
    
    		Assert.assertEquals(expected.toString(), tracker.toString());
		}

		sync(dst).makePath("to_be_deleted1");
		sync(dst).makePath("to_be_deleted2");
		sync(dst).makePath("to_be_retained1");
		sync(dst).makePath("to_be_retained2");
		write(sync(dst).openFileForWrite("to_be_deleted2/garbage.txt"), "garbage");
		write(sync(dst).openFileForWrite("to_be_retained2/garbage.log"), "garbage");
		write(sync(dst).openFileForWrite("src/main/root/override1/garbage.txt"), "garbage");
		
        {
            CopyBatch batch = sfcp.startBatch(sync(src));
            CopyTracker tracker = new CopyTracker(src, dst);
            batch.sourceExclude("**/.mkdir");
            batch.sourceExclude("**/b.txt");            
            batch.copy("src/main/root/b.txt.v2").rename("b.txt");
            batch.copy("**");
            batch.targetRetain("to_be_retained1/**");
            batch.targetRetain("to_be_retained2/*.log");
            batch.prepare(tracker);
            batch.execute(sync(dst), tracker);
            
            StringBuilder expected = new StringBuilder();
            expected.append("pom.xml -> pom.xml <match>").append("\n"); 
            expected.append("src/main/root/a.txt -> src/main/root/a.txt <match>").append("\n"); 
            expected.append("src/main/root/b.txt.v2 -> src/main/root/b.txt <rewrite 04%>").append("\n"); 
            expected.append("src/main/root/override1 -> src/main/root/override1 <dir>").append("\n"); 
            expected.append(" -> src/main/root/override1/garbage.txt <prune>").append("\n"); 
            expected.append("src/main/root/override2 -> src/main/root/override2 <dir>").append("\n"); 
            expected.append("src/main/root/x.prop -> src/main/root/x.prop <match>").append("\n"); 
            expected.append(" -> to_be_deleted1 <prune>").append("\n"); 
            expected.append(" -> to_be_deleted2 <prune>").append("\n"); 
    
            Assert.assertEquals(expected.toString(), tracker.toString());
        }
	}

	private void write(OutputStream stream, String text) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(stream);
        writer.append(text);
        writer.close();        
    }

    @Test
	public void verify_prune_1() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
		String src = "src/test/resources/SimpleFileCopyProcessor/root1";
		String dst = TARGET_PATH + "/" + methodName();
		CopyBatch batch = sfcp.startBatch(sync(src));
		CopyTracker tracker = new CopyTracker(src, dst);
		batch.sourceExclude("**/.mkdir");
        batch.sourceExclude("**/*.v2");		
		batch.copy("**");
		batch.sourcePrune("**");
        batch.prepare(tracker);
		batch.execute(sync(dst), tracker);
		
		StringBuilder expected = new StringBuilder();
		expected.append("pom.xml -> pom.xml <copy>").append("\n"); 
		expected.append("src/main/root/a.txt -> src/main/root/a.txt <copy>").append("\n"); 
		expected.append("src/main/root/b.txt -> src/main/root/b.txt <copy>").append("\n"); 
		expected.append("src/main/root/x.prop -> src/main/root/x.prop <copy>").append("\n"); 

		Assert.assertEquals(expected.toString(), tracker.toString());
	}

	@Test
	public void verify_prune_2() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
		String src = "src/test/resources/SimpleFileCopyProcessor/root1";
		String dst = TARGET_PATH + "/" + methodName();
		CopyBatch batch = sfcp.startBatch(sync(src));
		CopyTracker tracker = new CopyTracker(src, dst);
		batch.sourceExclude("**/.mkdir");
        batch.sourceExclude("**/*.v2");		
		batch.copy("**");
		batch.sourcePrune("**/*2");
        batch.prepare(tracker);		
		batch.execute(sync(dst), tracker);
		
		StringBuilder expected = new StringBuilder();
		expected.append("pom.xml -> pom.xml <copy>").append("\n"); 
		expected.append("src/main/root/a.txt -> src/main/root/a.txt <copy>").append("\n"); 
		expected.append("src/main/root/b.txt -> src/main/root/b.txt <copy>").append("\n"); 
		expected.append("src/main/root/override1 -> src/main/root/override1 <dir>").append("\n"); 
		expected.append("src/main/root/x.prop -> src/main/root/x.prop <copy>").append("\n"); 
		
		Assert.assertEquals(expected.toString(), tracker.toString());
	}

	@Test
	public void verify_rename_absolute() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
		String src = "src/test/resources/SimpleFileCopyProcessor/root1";
		String dst = TARGET_PATH + "/" + methodName();
		CopyBatch batch = sfcp.startBatch(sync(src));
		CopyTracker tracker = new CopyTracker(src, dst);
		batch.copy("**/pom.xml");
		batch.copy("**/a.txt").rename("/AAA.txt");
		batch.sourcePrune("**");
        batch.prepare(tracker);
		batch.execute(sync(dst), tracker);
		
		StringBuilder expected = new StringBuilder();
		expected.append("src/main/root/a.txt -> AAA.txt <copy>").append("\n"); 
		expected.append("pom.xml -> pom.xml <copy>").append("\n"); 
		
		Assert.assertEquals(expected.toString(), tracker.toString());
	}

	@Test
	public void verify_rename_relative() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
		String src = "src/test/resources/SimpleFileCopyProcessor/root1";
		String dst = TARGET_PATH + "/" + methodName();
		CopyBatch batch = sfcp.startBatch(sync(src));
		CopyTracker tracker = new CopyTracker(src, dst);
		batch.sourceExclude("**/.mkdir");
        batch.sourceExclude("**/*.v2");		
		batch.copy("**/a.txt").rename("AAA.txt");
		batch.copy("**");
		batch.sourcePrune("**");
        batch.prepare(tracker);
		batch.execute(sync(dst), tracker);
		
		StringBuilder expected = new StringBuilder();
		expected.append("pom.xml -> pom.xml <copy>").append("\n"); 
		expected.append("src/main/root/a.txt -> src/main/root/AAA.txt <copy>").append("\n"); 
		expected.append("src/main/root/b.txt -> src/main/root/b.txt <copy>").append("\n"); 
		expected.append("src/main/root/x.prop -> src/main/root/x.prop <copy>").append("\n"); 
		
		Assert.assertEquals(expected.toString(), tracker.toString());
	}

	@Test
	public void verify_multi_copy_relative() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
	    String src = "src/test/resources/SimpleFileCopyProcessor/root1";
	    String dst = TARGET_PATH + "/" + methodName();
	    CopyBatch batch = sfcp.startBatch(sync(src));
	    CopyTracker tracker = new CopyTracker(src, dst);
	    batch.sourceExclude("**/.mkdir");
        batch.sourceExclude("**/*.v2");	    
	    batch.copy("src/main/", "copy_2/", "root/**.txt");
	    batch.copy("src/main/root/", "copy_1/", "**.prop");
        batch.prepare(tracker);
	    batch.execute(sync(dst), tracker);
	    
	    StringBuilder expected = new StringBuilder();
	    expected.append("src/main/root/x.prop -> copy_1/x.prop <copy>").append("\n"); 
	    expected.append("src/main/root/a.txt -> copy_2/root/a.txt <copy>").append("\n"); 
	    expected.append("src/main/root/b.txt -> copy_2/root/b.txt <copy>").append("\n"); 
	    
	    Assert.assertEquals(expected.toString(), tracker.toString());
	}

	@Test
	public void verify_target_clash() throws IOException {
	    SimpleFileSyncProcessor sfcp = new SimpleFileSyncProcessor();
		String src = "src/test/resources/SimpleFileCopyProcessor/root1";
		String dst = TARGET_PATH + "/" + methodName();
		CopyBatch batch = sfcp.startBatch(sync(src));
		CopyTracker tracker = new CopyTracker(src, dst);
		batch.copy("**/*.txt").rename("AAA.txt");
		batch.copy("**");
		batch.sourcePrune("**");
		try {
	        batch.prepare(tracker);
			batch.execute(sync(dst), tracker);
			Assert.fail("Exception expected");
		}
		catch(RuntimeException e) {
			Assert.assertEquals("Target path collision detected", e.getMessage());
		}
		
		StringBuilder expected = new StringBuilder();
		expected.append("src/main/root/a.txt -> src/main/root/AAA.txt Target path collision").append("\n"); 
		expected.append("src/main/root/b.txt -> src/main/root/AAA.txt Target path collision").append("\n"); 
		
		Assert.assertEquals(expected.toString(), tracker.toString());
	}
	
	private static class CopyTracker implements CopyReporter {

		@SuppressWarnings("unused")
        String src;
		@SuppressWarnings("unused")
        String dst;
		StringBuilder sb = new StringBuilder();
		
		public CopyTracker(String source, String dest) {
			this.src = source;
			this.dst = dest;
		}

		@Override
		public void report(String source, String destination, String remark) {
			String sp = source;
			if (sp.endsWith("/")) {
			    sp = sp.substring(0, sp.length() - 1);
			}
			sp = sp.replace('\\', '/');
			String dp = destination;
            if (dp.endsWith("/")) {
                dp = dp.substring(0, dp.length() - 1);
            }
			dp = dp.replace('\\', '/');
			String line = sp + " -> " + dp + " " + remark;
			System.out.println(line);
			sb.append(line).append('\n');
		}
		
		@Override
		public String toString() {
			return sb.toString();
		}
	}
}
