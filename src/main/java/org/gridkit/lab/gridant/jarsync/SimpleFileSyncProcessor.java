package org.gridkit.lab.gridant.jarsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
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

import org.gridkit.lab.gridant.jarsync.BatchCopyProcessor.CopyReporter;
import org.metastatic.rsync.ChecksumPair;
import org.metastatic.rsync.DataBlock;
import org.metastatic.rsync.Delta;
import org.metastatic.rsync.Offsets;
import org.metastatic.rsync.Rdiff;

class SimpleFileSyncProcessor implements BatchCopyProcessor {

	@Override
	public CopyBatch startBatch(String path) {
		return new Batch(path);
	}

	private static class Batch implements CopyBatch {
		
		private File rootFile;
		private Map<String, File> remainder = new TreeMap<String, File>();
		private Map<String, Action> actions = new TreeMap<String, Action>();
		private AntPathMatcher pathMatcher = new AntPathMatcher();
		private List<String> targetExcludes = new ArrayList<String>(); 

		public Batch(String path) {
			rootFile = new File(path);
			if (!rootFile.exists()) {
				throw new IllegalArgumentException("Path [" + path + "] does not exists");
			}
			if (rootFile.isFile()) {
				throw new IllegalArgumentException("Path [" + path + "] is not a directory");
			}
			else {
				collectTree(rootFile, "");
			}
		}
		
		
		private void collectTree(File parent, String path) {
			File[] files = parent.listFiles();
			if (files != null) {
				for(File f: files) {
					if (f.isDirectory()) {
						remainder.put(path + f.getName() + "/", f);
					}
					else {
						remainder.put(path + f.getName(), f);
					}
					collectTree(f, path + f.getName() + "/");
				}
			}
		}

		@Override
        public void targetRetain(String pattern) {
		    targetExcludes.add(pattern);
        }

        @Override
		public CopyOptions copy(String pattern) {
			Map<String, CopyAction> actions = new TreeMap<String, CopyAction>();
			for(String path: remainder.keySet()) {
				if (pathMatcher.match(pattern, path)) {
					CopyAction a = new CopyAction(path, remainder.get(path));
					actions.put(path, a);
					this.actions.put(path, a);
				}
			}
			remainder.keySet().removeAll(actions.keySet());
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
		    for(String path: remainder.keySet()) {
		        if (pathMatcher.match(rebase(sourceBase, pattern), path)) {
		            CopyAction a = new CopyAction(path, remainder.get(path));
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
		    remainder.keySet().removeAll(actions.keySet());
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
			for(String path: remainder.keySet()) {
				if (pathMatcher.match(pattern, path)) {
					paths.add(path);
				}
			}
			remainder.keySet().removeAll(paths);
		}
		
		@Override
		public void sourcePrune(String pattern) {
			Set<String> paths = new HashSet<String>();
			for(String path: actions.keySet()) {
			    CopyAction action = (CopyAction) actions.get(path);
				if (action.source.isDirectory()) {
					if (pathMatcher.match(pattern, path)) {
						paths.add(path);
					}
				}
			}
			actions.keySet().removeAll(paths);
		}
		
		@Override
		public void execute(FileSyncSlave syncSlave, CopyReporter reporter) throws IOException {
			Rdiff rdiff = new Rdiff();
			autoprune();
			eraseTarget(syncSlave);
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
					action.perform(rdiff, syncSlave, reporter);
				}
			}
		}

		private void eraseTarget(FileSyncSlave syncTarget) {
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



        private void eraseTarget(SortedSet<String> retained, SortedSet<String> deleted, SortedSet<String> created, FileSyncSlave syncTarget, String path) {
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

        void perform(Rdiff rdiff, FileSyncSlave syncTarget, CopyReporter reporter) throws IOException;
	    
	}
	
	private static class TargetClean implements Action {
	    
	    private String targetPath;
	    private boolean isDir;

	    public TargetClean(String targetPath, boolean isDir) {
            this.targetPath = targetPath;
            this.isDir = isDir;
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
        public void perform(Rdiff rdiff, FileSyncSlave syncTarget, CopyReporter reporter) throws IOException {
	        if (isDir) {
	            syncTarget.eraseDirectory(targetPath);
	            reporter.report("", targetPath + "/", "<prune>");
	        }
	        else {
	            syncTarget.eraseFile(targetPath);
	            reporter.report("", targetPath, "<prune>");
	        }
        }
	}
	
	private static class CopyAction implements CopyOptions, Action {
		
		private File source;
		private String sourcePath;
		private String targetPath;
		
		public CopyAction(String path, File source) {
			this.source = source;
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
		public void perform(Rdiff rdiff, FileSyncSlave syncTarget, CopyReporter reporter) throws IOException {
			try {
				if (source.isDirectory()) {
			        reporter.report(sourcePath, targetPath, "<dir>");
				}
				else {
				    List<ChecksumPair> digest = syncTarget.readChecksums(targetPath);
				    FileInputStream fis = new FileInputStream(source);
				    if (digest.isEmpty()) {
    					OutputStream os = syncTarget.openFile(targetPath);
    					StreamHelper.copy(fis, os);
    					os.close();
    					reporter.report(sourcePath, targetPath, "<copy>");
				    }
				    else {
				        List<Delta> deltas = produceDeltas(rdiff, digest, fis);
				        long dataSize = dataSize(deltas);
				        long fileSize = source.length();
				        syncTarget.patchFile(targetPath, deltas);
				        boolean trim = isOffsetOnly(deltas);
				        boolean match = trim && doesMatchSize(deltas, fileSize);
				        if (match) {
				            reporter.report(sourcePath, targetPath, String.format("<match>"));
				        }
				        else if (trim) {
				            reporter.report(sourcePath, targetPath, String.format("<shuffle>"));
				        }
				        else {
				            reporter.report(sourcePath, targetPath, String.format("<rewrite %02.0f%%>", 100f * dataSize / fileSize));
				        }
				    }
				    fis.close();
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

        private boolean doesMatchSize(List<Delta> deltas, long fileSize) {
            Offsets offs = (Offsets) deltas.get(deltas.size() - 1);
            return fileSize == offs.getWriteOffset() + offs.getBlockLength();
        }
        
        private List<Delta> produceDeltas(Rdiff rdiff, List<ChecksumPair> digest, FileInputStream fis) throws IOException {
            try {
                return rdiff.makeDeltas(digest, fis);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
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
}
