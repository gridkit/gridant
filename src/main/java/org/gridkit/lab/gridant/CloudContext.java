package org.gridkit.lab.gridant;

import java.util.HashSet;
import java.util.Set;

import org.apache.tools.ant.Project;
import org.gridkit.nanocloud.CloudFactory;
import org.gridkit.vicluster.ViNode;
import org.gridkit.vicluster.ViNodeSet;

public class CloudContext {

	public synchronized static CloudContext getInstance(Project project) {
		CloudContext ctx = project.getReference(GridAntRefs.CLOUD_CONTEXT);
		if (ctx == null) {
			throw new IllegalArgumentException("Cloud context is not configured");
		}
		return ctx;
	}

	public synchronized static CloudContext ensureInstance(Project project) {
		CloudContext ctx = project.getReference(GridAntRefs.CLOUD_CONTEXT);
		if (ctx == null) {
			ctx = new CloudContext();
			project.addReference(GridAntRefs.CLOUD_CONTEXT, ctx);
		}
		return ctx;
	}
	
	private ViNodeSet nodeset = CloudFactory.createCloud();
	private Set<String> specificNodes = new HashSet<String>();
	
	public ViNodeSet getNodeSet() {
		return nodeset;
	}
	
	public ViNode initNode(String name) {
		if (name.indexOf('?') >= 0 || name.indexOf('*') >= 0) {
			throw new IllegalArgumentException("Specific node name should not contain wild cards. \"" + name + "\"");
		}
		if (!specificNodes.add(name)) {
			throw new IllegalStateException("Node '" + name + "' is already declared");
		}
		return nodeset.node(name);
	}
	
	
	
}
