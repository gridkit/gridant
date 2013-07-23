package org.gridkit.lab.gridant;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URL;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.MagicNames;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.ProjectHelperRepository;
import org.apache.tools.ant.RuntimeConfigurable;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.helper.AntXMLContext;
import org.apache.tools.ant.types.resources.URLResource;
import org.gridkit.vicluster.MassExec;
import org.gridkit.vicluster.ViNode;
import org.gridkit.zeroio.WrapperOutputStream;

public class RemoteExecTask extends Task implements TaskContainer {
	
	private String origPattern;
	private List<String> patterns = new ArrayList<String>();
	private List<TaskData> tasks = new ArrayList<TaskData>();
	private RemoteExecutionHost execHost;
	
	public void setServers(String servers) {
		origPattern = servers;
		String[] sp = servers.split("[,]");
		for(String p: sp) {
			p = p.trim();
			if (p.length() > 0) {
				patterns.add(p);
			}
		}
	}
	
	@Override
	public void addTask(Task task) {
		tasks.add(new TaskData((UnknownElement)task));
	}
	
	@Override
	public void execute() throws BuildException {
		
		CloudContext cc = CloudContext.getInstance(getProject());
		
		Map<String, ViNode> targets = new TreeMap<String, ViNode>();
		for(String pattern: patterns) {
			for(ViNode node: cc.getNodeSet().listNodes(pattern)) {
				String name = node.toString();
				targets.put(name, node);
			}
		}
		if (targets.isEmpty()) {
			System.out.println("Target execution set '" + origPattern + "' has been resolved to empty list");
		}
		else {
			// touch
			cc.getNodeSet().nodes(patterns.toArray(new String[0])).touch();
			
			List<Future<Void>> submissions = new ArrayList<Future<Void>>();
			
			for(ViNode node: targets.values()) {
				final String hostname = node.exec(new Callable<String>(){
					@Override
					public String call() throws Exception {
						return InetAddress.getLocalHost().getHostName();
					}
				});
				final LatentProject slave = createSlaveProject(node.toString(), hostname);
				final List<TaskData> script = tasks;
				final String nodeName = node.toString();
				final String sourceFile = filename(getLocation().getFileName());
				final int sourceLine = getLocation().getLineNumber();
				
				Future<Void> future = node.submit(new Callable<Void>(){
					@Override
					public Void call() throws Exception {
						executeRemoteTasks(slave, hostname, sourceFile, sourceLine, script);
						return null;
					}
				});
				System.out.println(" -> " + hostname + " (" + nodeName + ")");
				
				submissions.add(future);
			}			
			MassExec.waitAll(submissions);
		}
	}
	
	private String filename(String fileName) {
		int c = fileName.lastIndexOf('/');
		if (c >= 0) {
			return fileName.substring(c + 1);
		}
		c = fileName.lastIndexOf('\\');
		if (c >= 0) {
			return fileName.substring(c + 1);
		}
		return fileName;
	}

	private LatentProject createSlaveProject(String id, String hostname) {
		try {
			
			if (execHost == null) {
				execHost = new RemoteExecutionHost(getProject());
			}
			
			// TODO a couple of hacks here to 
			AntXMLContext xctx = getProject().getReference("ant.parsing.context");
			URL buildFile = xctx.getBuildFileURL();
			if (buildFile == null) {
				buildFile = xctx.getBuildFile().toURI().toURL();
			}
			
			SlaveProject slave =  new SlaveProject();
			slave.name = getProject().getName();
			slave.id = id;
			slave.hostname = hostname;
			slave.executor = execHost;
			slave.buildFile = buildFile.toURI().toString();
			slave.logger = new RemoteBuildLogger(createRemoteLogger(id), getProject());
			return slave;
			
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static interface LatentProject {
		public Project getProject();		
	}
	
	private static class SlaveProject implements LatentProject, Serializable {
		
		private static final long serialVersionUID = 20130715L;
		
		String id;
		String name;
		String hostname;
		BuildLogger logger;
		MasterExecutor executor;
		String buildFile;
		
		@Override
		public Project getProject() {
			try {
				
				MasterURLHandler mh = new MasterURLHandler(executor);
				
				URL rurl = new URL(buildFile);
				
				URL buildFileUrl = new URL(rurl, rurl.getFile(), mh); 
				URLResource res = new URLResource(buildFileUrl);
				
				Project project = new Project();
				
				ProjectHelper helper = ProjectHelperRepository.getInstance().getProjectHelperForBuildFile(res);
				project.addReference(MagicNames.REFID_PROJECT_HELPER, helper);
				project.addReference(GridAntRefs.MASTER_EXECUTOR, executor);

				String baseDir = System.getProperty(GridAntProps.REMOTE_ANT_BASE_DIR);
				if (baseDir != null) {
					new File(baseDir).mkdirs(); 
					project.setProperty("basedir", baseDir);
				}
				else {
					project.setProperty("basedir", ".");
				}
				project.setProperty("ant.file", buildFile);
				project.setProperty(GridAntProps.SLAVE_HOSTNAME, hostname);
				project.setProperty(GridAntProps.SLAVE_ID, id);

				helper.parse(project, res);
				project.setName(name + " @ " + hostname);
				
				project.addBuildListener(logger);
				
				return project;
			} catch (BuildException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
    private static BuildLogger createRemoteLogger(String serverId) {
    	BuildLogger logger = null;
    	logger = new DefaultLogger();
    	
    	logger.setMessageOutputLevel(Project.MSG_INFO);
    	logger.setOutputPrintStream(new PrintStream(new WrapperOutputStream("(" + serverId + ") -> ", OutputStreamHelper.stdOut)));
    	logger.setErrorPrintStream(new PrintStream(new WrapperOutputStream("(" + serverId + ") -> ", OutputStreamHelper.stdErr)));
    	logger.setEmacsMode(false);
    	
    	return logger;
    }
	
	private static void executeRemoteTasks(LatentProject lp, String hostname, String buildSource, int sourceLine, List<TaskData> script) {
		Project project = lp.getProject();

		Target target = new Target();
		String targetName = buildSource + ":" + sourceLine + " @ " + hostname;
		target.setName(targetName);
		target.setProject(project);
		
		for(TaskData td: script) {
			Task task = td.instantiate(project);
			target.addTask(task);
		}
		
		project.addTarget(target);
		
		OutputStreamHelper.activate(project);
		project.executeTarget(targetName);
		OutputStreamHelper.restore();
	}

	private static class TaskData implements Serializable {
		
		private static final long serialVersionUID = 20130715L;
		
		String tag;
		String namespace;
		String qname;
		String taskType;
		String taskName;
		Location location; 
		RuntimeConfigurable conf;
		
		List<TaskData> children = new ArrayList<TaskData>();
		
		public TaskData(UnknownElement task) {
			
	        tag = task.getTag();
	        namespace = task.getNamespace();
	        qname = task.getQName();
	        taskType = task.getTaskType();
	        taskName = task.getTaskName();
	        location = task.getLocation();

	        conf = new RuntimeConfigurable(new ProxyMock(), task.getTaskName());
	        conf.setPolyType(task.getWrapper().getPolyType());
	        
	        Map<String, Object> m = task.getWrapper().getAttributeMap();
	        for (Map.Entry<String, Object> entry : m.entrySet()) {
	        	conf.setAttribute(entry.getKey(), (String) entry.getValue());
	        }
	        conf.addText(task.getWrapper().getText().toString());

	        for (Enumeration<RuntimeConfigurable> e = task.getWrapper().getChildren(); e.hasMoreElements();) {
	            RuntimeConfigurable r = e.nextElement();
	            UnknownElement ueChild = (UnknownElement) r.getProxy();
	            TaskData child = new TaskData(ueChild);
	            children.add(child);
	        }
		}
		
		public UnknownElement instantiate(Project project) {
			UnknownElement ret = new UnknownElement(tag);
	        ret.setNamespace(namespace);
	        ret.setProject(project);
	        ret.setQName(qname);
	        ret.setTaskType(taskType);
	        ret.setTaskName(taskName);
	        ret.setLocation(location);

	        RuntimeConfigurable rc = new RuntimeConfigurable(ret, taskName);
	        rc.setPolyType(conf.getPolyType());
	        Map<String, Object> m = conf.getAttributeMap();
	        for (Map.Entry<String, Object> entry : m.entrySet()) {
	            rc.setAttribute(entry.getKey(), (String) entry.getValue());
	        }
	        rc.addText(conf.getText().toString());

	        for(TaskData child: children) {
	        	UnknownElement ue = child.instantiate(project);
	        	rc.addChild(ue.getWrapper());
	        }
	        
	        return ret;
		}
	}
	
	private static class ProxyMock implements Serializable {

		private static final long serialVersionUID = 20130715L;
		
	}
	
	private interface RemoteMasterExecutor extends MasterExecutor, Remote {
		
	}
	
    private class RemoteExecutionHost implements RemoteMasterExecutor {
    	
    	private Project project;
    	
    	private ExecutorService service = Executors.newCachedThreadPool(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setDaemon(true);
				return t;
			}
		});

    	public RemoteExecutionHost(Project project) {
    		this.project = project;
    	}
    	
		@Override
		public <T> T exec(final MasterCallable<T> task) {
			try {
				return service.submit(new Callable<T>(){
					@Override
					public T call() throws Exception {
						return task.call(project);
					}
				}).get();
			} catch (InterruptedException e) {
				throwUncheked(e);
				throw new Error("Unreachable");
			} catch (ExecutionException e) {
				throwUncheked(e.getCause());
				throw new Error("Unreachable");
			}
		}
    }
    
	private static void throwUncheked(Throwable e) {
		RemoteExecTask.<RuntimeException>throwAny(e);
	}
	
	@SuppressWarnings("unchecked")
	private static <E extends Throwable> void throwAny(Throwable e) throws E {
		throw (E)e;
	}	
}
