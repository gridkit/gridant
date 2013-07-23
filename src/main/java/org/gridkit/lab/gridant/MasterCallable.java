package org.gridkit.lab.gridant;

import org.apache.tools.ant.Project;

public interface MasterCallable<T> {

	public T call(Project project) throws Exception;
	
}
