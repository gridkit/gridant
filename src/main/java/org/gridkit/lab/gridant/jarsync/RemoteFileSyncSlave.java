package org.gridkit.lab.gridant.jarsync;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.rmi.Remote;
import java.util.List;

import org.gridkit.lab.gridant.jarsync.jarsync.ChecksumPair;
import org.gridkit.lab.gridant.jarsync.jarsync.Delta;
import org.gridkit.vicluster.telecontrol.ssh.OutputStreamRemoteAdapter;

public class RemoteFileSyncSlave implements FileSyncSlave, Serializable {

    private static final long serialVersionUID = 20140426L;
    
    @SuppressWarnings("unused")
    private transient FileSyncSlave originalTarget;
    private final FileSyncSlave proxyTarget;
    
    public RemoteFileSyncSlave(FileSyncSlave target) {
        this.originalTarget = target;
        this.proxyTarget = new RemoteSkeleton(target);
    }

    public String resolvePath(String path) {
        return proxyTarget.resolvePath(path);
    }

    public List<String> listDirectories(String path) {
        return proxyTarget.listDirectories(path);
    }

    public List<String> listFiles(String path) {
        return proxyTarget.listFiles(path);
    }

    public List<ChecksumPair> readChecksums(String path) throws IOException {
        return proxyTarget.readChecksums(path);
    }

    public boolean makePath(String path) throws IOException {
        return proxyTarget.makePath(path);
    }

    public OutputStream openFile(String path) throws IOException {
        return proxyTarget.openFile(path);
    }

    public void patchFile(String path, List<Delta> deltas) throws IOException {
        proxyTarget.patchFile(path, deltas);
    }

    public void eraseFile(String path) throws IOException {
        proxyTarget.eraseFile(path);
    }

    public void eraseDirectory(String path) throws IOException {
        proxyTarget.eraseDirectory(path);
    }

    private static interface RFileSyncSlave extends FileSyncSlave, Remote {
        
    }
    
    private static class RemoteSkeleton implements RFileSyncSlave {
        
        private final FileSyncSlave slave;

        public RemoteSkeleton(FileSyncSlave slave) {
            this.slave = slave;
        }

        public String resolvePath(String path) {
            return slave.resolvePath(path);
        }

        public List<String> listDirectories(String path) {
            return slave.listDirectories(path);
        }

        public List<String> listFiles(String path) {
            return slave.listFiles(path);
        }

        public List<ChecksumPair> readChecksums(String path) throws IOException {
            return slave.readChecksums(path);
        }

        public boolean makePath(String path) throws IOException {
            return slave.makePath(path);
        }

        public OutputStream openFile(String path) throws IOException {
            return new OutputStreamRemoteAdapter(slave.openFile(path));
        }

        public void patchFile(String path, List<Delta> deltas) throws IOException {
            slave.patchFile(path, deltas);
        }

        public void eraseFile(String path) throws IOException {
            slave.eraseFile(path);
        }

        public void eraseDirectory(String path) throws IOException {
            slave.eraseDirectory(path);
        }
    }
}
