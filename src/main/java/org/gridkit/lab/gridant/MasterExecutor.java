package org.gridkit.lab.gridant;

public interface MasterExecutor {

	public <T> T exec(MasterCallable<T> task);
	
}
