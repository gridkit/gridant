package org.gridkit.lab.gridant.jarsync;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.gridkit.lab.gridant.GridAntProps;
import org.gridkit.lab.gridant.GridAntRefs;
import org.gridkit.lab.gridant.MasterCallable;
import org.gridkit.lab.gridant.MasterExecutor;
import org.gridkit.lab.gridant.jarsync.BatchCopyProcessor.CopyBatch;
import org.gridkit.lab.gridant.jarsync.BatchCopyProcessor.CopyOptions;
import org.gridkit.lab.gridant.jarsync.BatchCopyProcessor.CopyReporter;

public class SyncDownTask extends Task {

    private String sourceBase;
    private String targetBase;
    private List<BatchConfElement> batchConfig = new ArrayList<BatchConfElement>();
    
    
    public void setSourceBase(String sourceBase) {
        this.sourceBase = sourceBase;
    }

    public void setTargetBase(String targetBase) {
        this.targetBase = targetBase;
    }
    
    public void addConfiguredRetain(Retain element) {
        if (element.pattern == null) {
            throw new IllegalArgumentException("Pattern required for <retain> element");
        }
        batchConfig.add(element);
    }

    public void addConfiguredExclude(Exclude element) {
        if (element.pattern == null) {
            throw new IllegalArgumentException("Pattern required for <exclude> element");
        }
        batchConfig.add(element);
    }

    public void addConfiguredPrune(Prune element) {
        if (element.pattern == null) {
            throw new IllegalArgumentException("Pattern required for <prune> element");
        }
        batchConfig.add(element);
    }

    public void addConfiguredCopy(Copy element) {
        batchConfig.add(element);
    }

    @Override
    public void execute() throws BuildException {
        
        MasterExecutor mexec = getProject().getReference(GridAntRefs.MASTER_EXECUTOR);
        if (mexec == null) {
            System.err.println("Master context is not avaliable");
            System.err.println("SyncDownTask can only be run in remote execution context");
        }
        
        File target = targetBase == null ? getProject().getBaseDir() : getProject().resolveFile(targetBase);
        SimpleSyncSlave sync = new SimpleSyncSlave(target);
        
        String name = getProject().getProperty(GridAntProps.SLAVE_ID);
        
        mexec.exec(new SyncExecutor(name, sourceBase, batchConfig, new RemoteFileSyncSlave(sync)));
    }
    
    private static void configure(CopyBatch batch, List<BatchConfElement> config) {
        for(BatchConfElement o: config) {
            o.configure(batch);
        }
    }
    
    private static class SyncExecutor implements MasterCallable<Void>, CopyReporter, Serializable {

        private static final long serialVersionUID = 20140426L;
        
        String node;
        String sourceBase; 
        List<BatchConfElement> config;
        FileSyncSlave sync;
        
        public SyncExecutor(String node,  String sourceBase, List<BatchConfElement> config, FileSyncSlave sync) {
            this.node = node;
            this.sourceBase = sourceBase;
            this.config = config;
            this.sync = sync;
        }

        @Override
        public Void call(Project project) throws Exception {
            File source = sourceBase == null ? project.getBaseDir() : project.resolveFile(sourceBase);
            SimpleFileSyncProcessor processor = new SimpleFileSyncProcessor();
            CopyBatch batch = processor.startBatch(source.getPath());
            configure(batch, config);
            batch.execute(sync, this);
            
            return null;
        }

        @Override
        public void report(String source, String destination, String remark) {
            if (source != null) {
                System.out.println(source + " -> " + node + ":" + destination + " " + remark);
            }
            else {
                System.out.println(node + ":" + destination + " " + remark);
            }
        }
    }
    
    private static interface BatchConfElement {
        
        public void configure(CopyBatch batch);
        
    }
    
    public static class Retain implements BatchConfElement, Serializable {
        
        private static final long serialVersionUID = 20140426L;
        
        String pattern;
        
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
        
        public void addText(String pattern) {
            if (pattern.trim().length() > 0) {
                if (this.pattern != null) {
                    throw new IllegalStateException("You cannot add text 'pattern' is already set");
                }
                this.pattern = pattern;
            }
        }

        @Override
        public void configure(CopyBatch batch) {
            batch.targetRetain(pattern);
        }
    }

    public static class Exclude implements BatchConfElement, Serializable {
        
        private static final long serialVersionUID = 20140426L;

        String pattern;
        
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
        
        public void addText(String pattern) {
            if (pattern.trim().length() > 0) {
                if (this.pattern != null) {
                    throw new IllegalStateException("You cannot add text 'pattern' is already set");
                }
                this.pattern = pattern;
            }
        }

        @Override
        public void configure(CopyBatch batch) {
            batch.sourceExclude(pattern);
        }
    }

    public static class Prune implements BatchConfElement, Serializable {
        
        private static final long serialVersionUID = 20140426L;

        String pattern;
        
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
        
        public void addText(String pattern) {
            if (pattern.trim().length() > 0) {
                if (this.pattern != null) {
                    throw new IllegalStateException("You cannot add text 'pattern' is already set");
                }
                this.pattern = pattern;
            }
        }

        @Override
        public void configure(CopyBatch batch) {
            if (pattern == null) {
                pattern = "**";
            }
            batch.sourcePrune(pattern);
        }
    }

    public static class Copy implements BatchConfElement, Serializable {
        
        private static final long serialVersionUID = 20140426L;

        String sourceBase;
        String targetBase;
        String rename;
        String pattern;
        
        public void setSource(String source) {
            this.sourceBase = source;
        }

        public void setTarget(String target) {
            this.targetBase = target;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public void setRename(String rename) {
            this.rename = rename;
        }
        
        public void addText(String pattern) {
            if (pattern.trim().length() > 0) {
                if (this.pattern != null) {
                    throw new IllegalStateException("You cannot add text 'pattern' is already set");
                }
                this.pattern = pattern;
            }
        }

        @Override
        public void configure(CopyBatch batch) {
            if (pattern == null) {
                pattern = "**";
            }
            CopyOptions opts;
            if (sourceBase == null && targetBase == null) {
                opts = batch.copy(pattern);
            }
            else {
                if (sourceBase == null) {
                    sourceBase = "";
                }
                else if (targetBase == null) {
                    targetBase = "";
                }
                opts = batch.copy(sourceBase, targetBase, pattern);
            }
            
            if (rename != null) {
                opts.rename(rename);
            }
        }
    }
}
