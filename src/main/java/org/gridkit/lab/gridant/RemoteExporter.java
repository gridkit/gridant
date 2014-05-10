package org.gridkit.lab.gridant;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.gridkit.util.concurrent.FutureBox;

class RemoteExporter {
	
	public static <T> T exportOneWay(T instance, Class<T> facade, Class<?>... otherFacades) {
		Class<?>[] ifs = new Class[otherFacades.length + 2];
		ifs[0] = facade;
		ifs[ifs.length - 1] = OneWayProxySync.class;
		System.arraycopy(otherFacades, 0, ifs, 1, otherFacades.length);
		return facade.cast(exportOneWay(instance, Arrays.asList(ifs)));
	}
	
	public static Object exportOneWay(Object instance, List<Class<?>> facades) {
		OneWayHandler h = new OneWayHandler(instance);
		OneWayRedirector r = new OneWayRedirector(h);
		return Proxy.newProxyInstance(facades.get(0).getClassLoader(), facades.toArray(new Class<?>[0]), r);
	}
	
	public static void syncOneWayProxy(Object instance) {
	    if (instance instanceof OneWayProxySync) {
	        ((OneWayProxySync) instance).sync(null);
	    }
	    else {
	        throw new IllegalArgumentException("Not a one way proxy");
	    }
	}
		
	public interface OneWayRemoteInvocationHandler extends Remote {

		void invoke(List<CallPackage> pack);
		
	}
	
	private static class OneWayHandler implements OneWayRemoteInvocationHandler {
		
		private final Object target;
		private final Map<MethodInfo, Method> methodCache = new ConcurrentHashMap<MethodInfo, Method>(16, 0.75f, 1);

		public OneWayHandler(Object target) {
			this.target = target;
		}

		@Override
        public void invoke(List<CallPackage> pack) {
            for(CallPackage call: pack) {
                try {
                    invoke(call.method, call.arguments);
                }
                catch(ThreadDeath e) {
                    throw e;
                }
                catch(Throwable e) {
                    // ignore;
                }
            }            
        }

		public Object invoke(MethodInfo method, Object[] args) throws Throwable {
			if (!methodCache.containsKey(method)) {
				Method m = method.getMethod();
				methodCache.put(method, m);
			}
			Method m = methodCache.get(method);
			return m.invoke(target, args);
		}
	}
	
	private static class CallThread extends Thread {
	    
	    private final OneWayRemoteInvocationHandler handler;
	    private WeakReference<OneWayRedirector> proxy;
	    private BlockingQueue<CallPackage> queue = new LinkedBlockingQueue<CallPackage>();
	    
	    
        public CallThread(OneWayRemoteInvocationHandler handler, OneWayRedirector redir) {
            this.handler = handler;
            this.proxy = new WeakReference<OneWayRedirector>(redir);
            setName("CallThread-" + redir);
        }


        @Override
        public void run() {
            List<CallPackage> batch = new ArrayList<CallPackage>();
            try {
                while(true) {
                    CallPackage pack = queue.poll(1000, TimeUnit.MILLISECONDS);
                    if (pack != null) {
                        batch.add(pack);
                        queue.drainTo(batch);
                        List<FutureBox<Object>> syncs = extractSyncs(batch);
                        handler.invoke(batch);
                        batch.clear();
                        if (syncs != null) {
                            for(FutureBox<Object> sync: syncs) {
                                sync.setData(null);
                            }
                        }
                    }
                    else {
                        if (proxy.get() == null && queue.isEmpty()) {
                            break;
                        }
                    }                
                }
            } catch (InterruptedException e) {
                // breaking out
            }
        }

        private List<FutureBox<Object>> extractSyncs(List<CallPackage> batch) {
            List<FutureBox<Object>> result = null;
            Iterator<CallPackage> it = batch.iterator();
            while(it.hasNext()) {
                CallPackage p = it.next();
                if (p.sync != null) {
                    if (result == null) {
                        result = new ArrayList<FutureBox<Object>>();
                    }
                    result.add(p.sync);
                    it.remove();
                }
            }
            return result;
        }
	}
	
	@SuppressWarnings("serial")
	private static class OneWayRedirector implements InvocationHandler, Serializable {
		
		private final OneWayRemoteInvocationHandler handler;
		private CallThread thread;

		public OneWayRedirector(OneWayRemoteInvocationHandler handler) {
			this.handler = handler;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getDeclaringClass() == Object.class) {
				try {
					return method.invoke(this, args);
				}
				catch(InvocationTargetException e) {
					throw e.getCause();
				}
			}
			else if (method.getDeclaringClass() == OneWayProxySync.class) {
			    FutureBox<Object> box = new FutureBox<Object>();
			    schedule(new CallPackage(box));
			    box.get();
			    return null;
			}
			else {
				MethodInfo mi = new MethodInfo(method);
				CallPackage pack = new CallPackage(mi, args);
				schedule(pack);
				return null;
			}
		}

        private void schedule(CallPackage pack) throws InterruptedException {
            synchronized(this) {
            	if (thread == null) {
            	    thread = new CallThread(handler, this);
            	    thread.start();
            	}
            	thread.queue.put(pack);
            }
        }
	}
	
	@SuppressWarnings("serial")
	private static class MethodInfo implements Serializable {
		
		private Class<?> type;
		private String name;
		private List<Class<?>> args;

		public MethodInfo(Method m) {
			type = m.getDeclaringClass();
			name = m.getName();
			args = Arrays.asList(m.getParameterTypes());
		}

		public Method getMethod() {
			try {
				Method m = type.getMethod(name, args.toArray(new Class[0]));
				m.setAccessible(true);
				return m;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((args == null) ? 0 : args.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((type == null) ? 0 : type.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MethodInfo other = (MethodInfo) obj;
			if (args == null) {
				if (other.args != null)
					return false;
			} else if (!args.equals(other.args))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}
	}
	
	private static class CallPackage implements Serializable {
	    
        private static final long serialVersionUID = 20140427L;
        
        MethodInfo method;
	    Object[] arguments;
	    FutureBox<Object> sync;
        
	    public CallPackage(MethodInfo method, Object[] arguments) {
            this.method = method;
            this.arguments = arguments;
            this.sync = null;
        }

	    public CallPackage(FutureBox<Object> sync) {
	        this.method = null;
	        this.arguments = null;
	        this.sync = sync;
	    }
	}
	
	public interface OneWayProxySync {
	    
	    public void sync(OneWayProxySync signatureGuard);
	}
}
