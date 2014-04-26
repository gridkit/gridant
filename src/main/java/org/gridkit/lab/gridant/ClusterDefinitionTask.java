package org.gridkit.lab.gridant;

import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.gridkit.nanocloud.RemoteNode;
import org.gridkit.vicluster.ViNode;
import org.gridkit.vicluster.ViProps;
import org.gridkit.vicluster.telecontrol.ssh.RemoteNodeProps;

public class ClusterDefinitionTask extends Task {
	
	public final static String TYPE_LOCAL = "local"; 
	public final static String TYPE_REMOTE = "remote"; 
	public final static String TYPE_IN_PROCESS = "in-process"; 

	static void checkType(String type) {
		if (!TYPE_LOCAL.equalsIgnoreCase(type)
				&& !TYPE_REMOTE.equalsIgnoreCase(type)
				&& !TYPE_IN_PROCESS.equalsIgnoreCase(type)) {
			throw new RuntimeException("Illegal type '" + type + "'");
		}
	}
	
	private String basePath;
	private String javaPath;
//	private String jarCachePath;
	private String type;
	
	private List<ConfigOption> configOptions = new ArrayList<ConfigOption>(); 
	private List<ServerDeclaration> serverDeclarations = new ArrayList<ServerDeclaration>(); 

	public void setType(String type) {
		new DefaultType().addText(type);
	}

	public void setJavapath(String path) {
		new JavaPath().addText(path);
	}

	public void setBasepath(String path) {
		new BasePath().addText(path);
	}
	
	public void addConfiguredServer(ServerDeclaration server) {
		if (server.id == null) {
			throw new IllegalArgumentException("No server id is specified");
		}
		serverDeclarations.add(server);
	}

	public void addConfiguredProp(ConfigOption option) {
		if (option.propName == null) {
			throw new IllegalArgumentException("No property name specified");
		}
		configOptions.add(option);
	}

	public BasePath createBasepath() {
		return new BasePath();
	}

	public JavaPath createJavapath() {
		return new JavaPath();
	}

	public DefaultType createType() {
		return new DefaultType();
	}
	
	@Override
	public void execute() throws BuildException {
		CloudContext ctx = CloudContext.ensureInstance(getProject());
		for(ServerDeclaration sd: serverDeclarations) {
			ViNode node = ctx.initNode(sd.id);
			if (sd.hostName != null) {
				RemoteNodeProps.at(node).setRemoteHost(sd.hostName);
			}
			String ntype = this.type;
			if (sd.type != null) {
				ntype = sd.type;
			}
			if (ntype == null) {
				ntype = TYPE_REMOTE;
			}
			setType(node, ntype);
			
			if (basePath != null) {
				node.setProp(GridAntProps.REMOTE_ANT_BASE_DIR, basePath);
			}
			if (javaPath != null) {
			    // TODO 0.7 to 0.8 migration atifact
				RemoteNodeProps.at(node).setRemoteJavaExec(javaPath);
				node.x(RemoteNode.REMOTE).setRemoteJavaExec(javaPath);
			}
			else {
				RemoteNodeProps.at(node).setRemoteJavaExec("java");
			}
			RemoteNodeProps.at(node).setRemoteJarCachePath("/tmp/.telecontrol");
			RemoteNodeProps.at(node).setSshConfig("?~/ssh-credentials.prop");
		}
		for(ConfigOption option: configOptions) {
			ctx.getNodeSet().node(option.nodePattern).setProp(option.propName, option.value);
		}
	}

	private void setType(ViNode node, String ntype) {
		if (TYPE_LOCAL.equals(ntype)) {
			ViProps.at(node).setLocalType();
		}
		else if (TYPE_IN_PROCESS.equals(ntype)) {
			ViProps.at(node).setIsolateType();
		}
		else if (TYPE_REMOTE.equals(ntype)) {
			ViProps.at(node).setRemoteType();
		}
		else {
			throw new IllegalArgumentException("Unknown node type '" + ntype + "'");
		}
	}

    public class BasePath {
		
		public void addText(String path) {
			if (basePath != null) {
				throw new RuntimeException("'basepath' is already set");
			}
			basePath = path;
		}
	}

	public class JavaPath {
		
		public void addText(String path) {
			if (javaPath != null) {
				throw new RuntimeException("'javapath' is already set");
			}
			javaPath = path;
		}
	}

	public class DefaultType {
		
		public void addText(String path) {
			if (type != null) {
				throw new RuntimeException("'type' is already set");
			}
			type = path;
		}
	}

	public static class ServerDeclaration {
		
		String id;
		String hostName;
//		String basePath;
//		String javaPath;
//		String jarCachePath;
		String type;
		
		public void setId(String id) {
			if (id.indexOf('?') >= 0 || id.indexOf('*') >= 0) {
				String error = "Invalid node id '" + id + "'";
				System.err.println("error");
				throw new RuntimeException(error);
			}
			this.id = id;
		}

		public void setHost(String host) {
			this.hostName = host;
		}

		public void setType(String type) {
			checkType(type);
			this.type = type;
		}		
	}
	
	public static class ConfigOption {
		
		String nodePattern = "**";
		String propName;
		String value = "";
		
		public void setOn(String nodePattern) {
			this.nodePattern = nodePattern;
		}

		public void setName(String name) {
			this.propName = name;
		}

		public void setValue(String value) {
			this.value = value;
		}
		
		public void addText(String value) {
			if (value.trim().length() > 0) {
				if (this.value != null) {
					throw new IllegalStateException("You cannot add text 'value' is already set");
				}
				this.value = value;
			}
		}
	}
}
