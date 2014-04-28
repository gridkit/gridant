package org.gridkit.lab.gridant.jarsync;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.gridkit.lab.gridant.jarsync.jarsync.ChecksumPair;
import org.gridkit.lab.gridant.jarsync.jarsync.DataBlock;
import org.gridkit.lab.gridant.jarsync.jarsync.Delta;

class SimpleFileSyncProcessor implements BatchCopyProcessor {

	@Override
	public CopyBatch startBatch(FileSyncParty source) {
		return new Batch(source);
	}

	private static class Batch implements CopyBatch, Serializable {
		
        private static final long serialVersionUID = 20140427L;
        
		private final class ErrorChecker implements CopyReporter {
            private final CopyReporter reporter;
            boolean error = false;

            private ErrorChecker(CopyReporter reporter) {
                this.reporter = reporter;
            }

            @Override
            public void report(String source, String destination, String remark) {
                error = true;
                reporter.report(source, destination, remark); 
            }
        }


        private transient FileSyncParty source;
		private FileSyncParty remoteSource;
		private Set<String> remainder = new TreeSet<String>();
		private Map<String, Action> actions = new TreeMap<String, Action>();
		private AntPathMatcher pathMatcher = new AntPathMatcher();
		private List<String> targetExcludes = new ArrayList<String>(); 

		public Batch(FileSyncParty source) {
			this.source = source;
			this.remoteSource = new RemoteFileSyncSlave(source);
			collectTree("");
		}
		
		
		private void collectTree(String path) {
			for(String f: source.listDirectories(path)) {
				String child = path + f + "/";
                remainder.add(child);
                collectTree(child);
			}
			for(String f: source.listFiles(path)) {
			    remainder.add(path + f);
			}
		}

		@Override
        public void targetRetain(String pattern) {
		    targetExcludes.add(pattern);
        }

        @Override
		public CopyOptions copy(String pattern) {
            Map<String, CopyAction> actions = new TreeMap<String, CopyAction>();
            for(String path: remainder) {
            	if (pathMatcher.match(pattern, path)) {
            		CopyAction a = new CopyAction(path);
            		actions.put(path, a);
            		this.actions.put(path, a);
            	}
            }
            remainder.removeAll(actions.keySet());
            return new ActionGroup(actions.values());
		}

		@Override
		public CopyOptions copy(String sourceBase, String targetBase, String pattern) {
		    if (targetBase == null || targetBase.length() == 0) {
		        targetBase = "/";
		    }
		    else {
		        if (!targetBase.startsWith("/")) {
		            targetBase = "/" + targetBase;
		        }
		    }
		    
		    Map<String, CopyAction> actions = new TreeMap<String, CopyAction>();
		    for(String path: remainder) {
		        if (pathMatcher.match(rebase(sourceBase, pattern), path)) {
		            CopyAction a = new CopyAction(path);
		            String tPath = path;
		            if (sourceBase != null && (!sourceBase.equals("."))) {
		                tPath = path.substring(sourceBase.length());
		                if (tPath.startsWith("/")) {
		                    tPath = tPath.substring(1);
		                }
		                tPath = (targetBase.endsWith("/") ? targetBase : targetBase + "/") + tPath;
		                a.rename(tPath);
		            }		            
		            actions.put(path, a);
		            this.actions.put(path, a);
		        }
		    }
		    remainder.removeAll(actions.keySet());
		    return new ActionGroup(actions.values());
		}
		
		private String rebase(String base, String pattern) {
		    if (base.equals(".") || base == null) {
		        return pattern;
		    }
		    if (base.trim().length() == 0 || base.startsWith("/") || base.indexOf('*') >= 0 || base.indexOf('?') >= 0) {
		        throw new IllegalArgumentException("Invalid base path");
		    }
		    return base.endsWith("/") ? base + pattern : base + "/" + pattern;
		}

		@Override
		public void sourceExclude(String pattern) {
			Set<String> paths = new HashSet<String>();
			for(String path: remainder) {
				if (pathMatcher.match(pattern, path)) {
					paths.add(path);
				}
			}
			remainder.removeAll(paths);
		}
		
		@Override
		public void sourcePrune(String pattern) {
			Set<String> paths = new HashSet<String>();
			for(String path: actions.keySet()) {
			    CopyAction action = (CopyAction) actions.get(path);
				if (action.sourcePath.endsWith("/")) {
					if (pathMatcher.match(pattern, path)) {
						paths.add(path);
					}
				}
			}
			actions.keySet().removeAll(paths);
		}
		
		@Override
        public void prepare(final CopyReporter reporter) throws IOException {
		    ErrorChecker delegate = new ErrorChecker(reporter);
            for(Action action: actions.values()) {
                action.prepare(source, reporter);                
            }
            if (delegate.error) {
                throw new IOException("Batch prepare has failed");
            }
        }

        @Override
		public void execute(FileSyncParty syncTarget, CopyReporter reporter) throws IOException {
			autoprune();
			eraseTarget(syncTarget);
			List<Action> alist = new ArrayList<Action>(actions.values());
			Collections.sort(alist, new Comparator<Action>() {

				@Override
				public int compare(Action o1, Action o2) {
					return o1.getTargetPath().compareTo(o2.getTargetPath());
				}
			});
			boolean clash = false;
			Action prev = null;
			boolean reported = false;
			for(Action a: alist) {
				if (prev != null) {
					if (a.getTargetPath().equals(prev.getTargetPath())) {
						if (!reported) {
							reporter.report(prev.getSourcePath(), prev.getTargetPath(), "Target path collision");
						}
						reporter.report(a.getSourcePath(), a.getTargetPath(), "Target path collision");
						reported = true;
						clash = true;
					}
					else {
						reported = false;
					}
				}
				prev = a;
			}	
			if (clash == true) {
				throw new RuntimeException("Target path collision detected");
			}
			else {
				for(Action action: alist) {
					action.perform(remoteSource, syncTarget, reporter);
				}
			}
		}

		private void eraseTarget(FileSyncParty syncTarget) {
		    SortedSet<String> retained = new TreeSet<String>();
		    SortedSet<String> deleted = new TreeSet<String>();
		    SortedSet<String> created = new TreeSet<String>();
		    for(Action a: actions.values()) {
		        created.add(a.getTargetPath());
		    }
            eraseTarget(retained, deleted, created, syncTarget, null);
            while(!deleted.isEmpty()) {
                String del = deleted.first();
                deleted.remove(del);
                if (del.endsWith("/")) {
                    if (subpath(retained, del).isEmpty() && subpath(created, del).isEmpty()) {
                        deleted.removeAll(subpath(deleted, del));
                        actions.put(del, new TargetClean(del.substring(0, del.length()), true));
                    }
                }
                else {
                    actions.put(del, new TargetClean(del, false));
                }
            }
        }

        private Collection<String> subpath(SortedSet<String> paths, String path) {
            SortedSet<String> sub = paths.tailSet(path);
            sub = sub.headSet(path + ((char)60000));
            
            return new ArrayList<String>(sub);
        }



        private void eraseTarget(SortedSet<String> retained, SortedSet<String> deleted, SortedSet<String> created, FileSyncParty syncTarget, String path) {
            fileLoop:
            for(String file : syncTarget.listFiles(path)) {
                String fpath = path == null ? file : path + "/" + file;
                if (created.contains(fpath)) {
                    continue;
                }
                for(String exclude: targetExcludes) {
                    if (pathMatcher.match(exclude, fpath)) {
                        retained.add(fpath);
                        continue fileLoop;
                    }
                }
                deleted.add(fpath);
            }            
            dirLoop:
            for(String file : syncTarget.listDirectories(path)) {
                String fpath = path == null ? file : path + "/" + file;
                for(String exclude: targetExcludes) {
                    if (exclude.equals(fpath + "/**")) {
                        retained.add(fpath + "/");
                        continue dirLoop;
                    }
                }
                eraseTarget(retained, deleted, created, syncTarget, fpath);
                deleted.add(fpath + "/");
            }            
        }


        /**
		 * Autoprune remove directories which will be created implicitly anyway.
		 * Main reason for this is reduce unnecessary report clutter.
		 */
		private void autoprune() {
			// Terribly inefficient implementation, but who cares
			for(String path: new ArrayList<String>(actions.keySet())) {
				Action action = actions.get(path);
				if (action != null) {
					String parent = action.getSourcePath();
					int n = parent.substring(0, parent.length() - 1).lastIndexOf('/');
					if (n >= 0) {
						parent = parent.substring(0, n);
						sourcePrune(parent + "/");
					}
				}
			}			
		}					
	}
	
	private static class ActionGroup implements CopyOptions {

		private Collection<CopyAction> actions;
		
		public ActionGroup(Collection<CopyAction> actions) {
			this.actions = actions;
		}

		@Override
		public CopyOptions rename(String newName) {
			for(CopyAction a: actions) {
				a.rename(newName);
			}
			return this;
		}
	}
	
	private static interface Action {

	    String getSourcePath();
	    
	    String getTargetPath();

        void prepare(FileSyncParty syncSource, CopyReporter reporter);

        void perform(FileSyncParty syncSource, FileSyncParty syncTarget, CopyReporter reporter) throws IOException;
	    
	}
	
	private static class TargetClean implements Action, Serializable {
	    
        private static final long serialVersionUID = 20140427L;
        
	    private String targetPath;
	    private boolean isDir;

	    public TargetClean(String targetPath, boolean isDir) {
            this.targetPath = targetPath;
            this.isDir = isDir;
            if (isDir && !targetPath.endsWith("/")) {
                this.targetPath = this.targetPath + "/";
            }
        }

        @Override
        public String getSourcePath() {
            return "";
        }
        
	    @Override
        public String getTargetPath() {
            return targetPath;
        }

	    @Override
        public void prepare(FileSyncParty syncSource, CopyReporter reporter) {
            // do nothing
        }

        @Override
        public void perform(FileSyncParty syncSource, FileSyncParty syncTarget, CopyReporter reporter) throws IOException {
	        if (isDir) {
	            syncTarget.eraseDirectory(targetPath);
	            reporter.report("", targetPath, "<prune>");
	        }
	        else {
	            syncTarget.eraseFile(targetPath);
	            reporter.report("", targetPath, "<prune>");
	        }
        }
	}
	
	private static class CopyAction implements CopyOptions, Action, Serializable {
		
        private static final long serialVersionUID = 20140427L;
        
        private String sourcePath;
		private String targetPath;
		private List<ChecksumPair> digest;
		
		public CopyAction(String path) {
			this.sourcePath = path;
			this.targetPath = path;			
			if (targetPath.startsWith("/")) {
			    targetPath = targetPath.substring(1);
			}
		}

		@Override
		public String getSourcePath() {
		    return sourcePath;
		}

		@Override
		public String getTargetPath() {
		    return targetPath;
		}
		
		@Override
		public void prepare(FileSyncParty syncSource, CopyReporter reporter) {
		    if (!sourcePath.endsWith("/")) {
		        try {
                    this.digest = syncSource.readChecksums(sourcePath);
                } catch (IOException e) {
                    reporter.report(sourcePath, "", "ERROR: " + e);
                }
		    }
		}
		
		@Override
		public void perform(FileSyncParty syncSource, FileSyncParty syncTarget, CopyReporter reporter) throws IOException {
			try {
				if (sourcePath.endsWith("/")) {
			        reporter.report(sourcePath, targetPath, "<dir>");
				}
				else {
				    List<ChecksumPair> digest = syncTarget.readChecksums(targetPath);
				    if (digest.isEmpty()) {
    					OutputStream os = syncTarget.openFileForWrite(targetPath);
    					syncSource.streamFile(sourcePath, os);
    					os.close();
    					reporter.report(sourcePath, targetPath, "<copy>");
				    }
				    else {
				        if (this.digest.equals(digest)) {
				            // files are identical
				            reporter.report(sourcePath, targetPath, String.format("<match>"));
				        }
				        else {
    				        List<Delta> deltas = syncSource.preparePatch(sourcePath, digest);
    				        long dataSize = dataSize(deltas);
    				        long fileSize = fileLength(this.digest);
    				        boolean trim = isOffsetOnly(deltas);
    				        syncTarget.applyPatch(targetPath, deltas);
    				        if (trim) {
    				            reporter.report(sourcePath, targetPath, String.format("<shuffle>"));
    				        }
    				        else {
    				            reporter.report(sourcePath, targetPath, String.format("<rewrite %02.0f%%>", 100f * dataSize / fileSize));
    				        }
				        }
				    }
				}
			} catch (RuntimeException e) {
				reporter.report(sourcePath, targetPath, "Error: " + e.toString());
				throw e;
			} catch (IOException e) {
				reporter.report(sourcePath, targetPath, "Error: " + e.toString());
				throw e;
			}
		}

        private boolean isOffsetOnly(List<Delta> deltas) {
            for(Delta delta: deltas) {
                if (delta instanceof DataBlock) {
                    return false;
                }
            }
            return true;
        }
        
		private long dataSize(List<Delta> deltas) {
		    long total = 0;
		    for(Delta d: deltas) {
		        if (d instanceof DataBlock) {
		            total += d.getBlockLength();
		        }
		    }
            return total;
        }

        @Override
		public CopyOptions rename(String newName) {
			if (newName.startsWith("/")) {
				targetPath = newName.substring(1);
			}
			else {
				targetPath = sourcePath.substring(0, sourcePath.lastIndexOf('/') + 1) + newName;
			}
			return this;
		}        
	}	
	
	static long fileLength(List<ChecksumPair> digest) {
	    if (digest.isEmpty()) {
	        return 0;
	    }
	    else {
	        ChecksumPair pair = digest.get(digest.size() - 1);
	        return pair.getOffset() + pair.getLength();
	    }
	}
}
