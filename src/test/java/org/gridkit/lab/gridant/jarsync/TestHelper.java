package org.gridkit.lab.gridant.jarsync;

import java.io.File;

public class TestHelper {

	public static String methodName() {
		return Thread.currentThread().getStackTrace()[2].getMethodName();
	}
	
	public static void rmrf(String path) {
		rmrf(new File(path));
	}

	public static void rmrf(File f) {
		if (f.exists()) {
			if (f.isDirectory()) {
				File[] cc = f.listFiles();
				if (cc != null) {
					for(File c: cc) {
						rmrf(c);
					}
				}
			}
			f.delete();
		}
	}
	
}
