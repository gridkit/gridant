package org.gridkit.lab.gridant.jarsync;

import java.io.IOException;


public interface BatchCopyProcessor {

	public CopyBatch startBatch(String path);
	
	public interface CopyBatch {

		public CopyOptions copy(String pattern);

		public CopyOptions copy(String sourcePath, String targetPath, String pattern);

		/**
		 * Pattern for target paths which should be
		 * excluded from synchronisation.
		 */
		public void targetRetain(String pattern);
		
		public void sourceExclude(String pattern);
		
		public void sourcePrune(String pattern);
		
		public void execute(FileSyncSlave syncTarget, CopyReporter reporter) throws IOException;
		
	}
	
	public interface CopyOptions {
		
		public CopyOptions rename(String newName);
		
	}
	
	public interface CopyReporter {
		
		public void report(String source, String destination, String remark);
		
	}
	
}
