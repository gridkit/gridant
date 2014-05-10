package org.gridkit.lab.gridant;

import java.io.PrintStream;
import java.io.Serializable;
import java.rmi.Remote;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.UnknownElement;

class RemoteBuildLogger implements BuildLogger, Serializable {
	
	private static final long serialVersionUID = 20130715L;
	
	private AntLogger logger;
	
	public RemoteBuildLogger(BuildLogger logger, Project proj) {
		this.logger = RemoteExporter.exportOneWay(new ProxyWrapper(logger, proj), AntLogger.class);
	}
	
	public void buildStarted(BuildEvent event) {
		logger.buildStarted(new RemoteBuildEvent(event));
	}


	public void buildFinished(BuildEvent event) {
		logger.buildFinished(new RemoteBuildEvent(event));
	}


	public void targetStarted(BuildEvent event) {
		logger.targetStarted(new RemoteBuildEvent(event));
	}


	public void targetFinished(BuildEvent event) {
		logger.targetFinished(new RemoteBuildEvent(event));
		RemoteExporter.syncOneWayProxy(logger);
	}

	public void taskStarted(BuildEvent event) {
		logger.taskStarted(new RemoteBuildEvent(event));
	}

	public void taskFinished(BuildEvent event) {
		logger.taskFinished(new RemoteBuildEvent(event));
		RemoteExporter.syncOneWayProxy(logger);
	}


	public void messageLogged(BuildEvent event) {
		logger.messageLogged(new RemoteBuildEvent(event));
	}


	@Override
	public void setMessageOutputLevel(int level) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setOutputPrintStream(PrintStream output) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setEmacsMode(boolean emacsMode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setErrorPrintStream(PrintStream err) {
		throw new UnsupportedOperationException();
	}
	
	static interface AntLogger {
	    
	    public void buildStarted(RemoteBuildEvent event);
	    
	    public void buildFinished(RemoteBuildEvent event);
	    
	    public void targetStarted(RemoteBuildEvent event);
	    
	    public void targetFinished(RemoteBuildEvent event);
	    
	    public void taskStarted(RemoteBuildEvent event);
	    
	    public void taskFinished(RemoteBuildEvent event);
	    
	    public void messageLogged(RemoteBuildEvent event);		
	}

	static interface RemoteLogger extends Remote, AntLogger {
		
	}
	
	private static class ProxyWrapper implements RemoteLogger {
		
		private BuildLogger logger;
		private Project project;

		public ProxyWrapper(BuildLogger logger, Project project) {
			this.logger = logger;
			this.project = project;
		}

		@Override
		public void buildStarted(RemoteBuildEvent event) {
			logger.buildStarted(event.toEvent(project));
		}

		@Override
		public void buildFinished(RemoteBuildEvent event) {
			logger.buildFinished(event.toEvent(project));
		}

		@Override
		public void targetStarted(RemoteBuildEvent event) {
			logger.targetStarted(event.toEvent(project));
		}

		@Override
		public void targetFinished(RemoteBuildEvent event) {
			logger.targetFinished(event.toEvent(project));
		}

		@Override
		public void taskStarted(RemoteBuildEvent event) {
			logger.taskStarted(event.toEvent(project));
		}

		@Override
		public void taskFinished(RemoteBuildEvent event) {
			logger.taskFinished(event.toEvent(project));
		}

		@Override
		public void messageLogged(RemoteBuildEvent event) {
			logger.messageLogged(event.toEvent(project));
		}
	}
	
	private static class RemoteBuildEvent implements Serializable {
		
		private static final long serialVersionUID = 20130715L;
		
		String target;
	    String taskTag;
	    String taskName;

	    String message;

	    int priority;
	    Throwable exception;

	    public RemoteBuildEvent(BuildEvent event) {
	    	if (event.getTarget() != null) {
	    		target = event.getTarget().getName();
	    	}
	    	if (event.getTask() != null) {
	    		taskTag = event.getTask().getRuntimeConfigurableWrapper().getElementTag();
	    		taskName = event.getTask().getTaskName();
	    	}
	    	message = event.getMessage();
	    	priority = event.getPriority();
	    	exception = event.getException();
	    }

	    public BuildEvent toEvent(Project proj) {
	    	BuildEvent be;
    		Target t = target == null ? null : proj.getTargets().get(target);
    		if (t == null) {
    			t = new Target();
    			t.setName(target);
    			t.setProject(proj);
    		}
    		if (taskTag == null) {
    			if (target == null) {
    				be = new BuildEvent(proj);
    			}
    			else {
    				be = new BuildEvent(t);
    			}
    		}
    		else {
    			UnknownElement ue = new UnknownElement(taskTag);
    			ue.setTaskName(taskName);
    			ue.setOwningTarget(t);
    			be = new BuildEvent(ue);
    		}
	    	be.setMessage(message, priority);
	    	be.setException(exception);
	    	
	    	return be;
	    }
	}
}
