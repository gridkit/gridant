package org.gridkit.lab.gridant.jarsync;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.rmi.Remote;
import java.util.List;

import org.gridkit.lab.gridant.jarsync.jarsync.ChecksumPair;
import org.gridkit.lab.gridant.jarsync.jarsync.Delta;
import org.gridkit.vicluster.telecontrol.ssh.OutputStreamRemoteAdapter;

public class RemoteFileSyncSlave implements FileSyncParty, Serializable {

    private static final long serialVersionUID = 20140426L;
    
    @SuppressWarnings("unused")
    private transient FileSyncParty originalTarget;
    private final FileSyncParty proxyTarget;
    
    public RemoteFileSyncSlave(FileSyncParty target) {
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

    public OutputStream openFileForWrite(String path) throws IOException {
        return proxyTarget.openFileForWrite(path);
    }
    
    public void streamFile(String path, OutputStream sink) throws IOException {
        proxyTarget.streamFile(path, new OutputStreamRemoteAdapter(sink));
    }

    public List<Delta> preparePatch(String path, List<ChecksumPair> digest) throws IOException {
        return proxyTarget.preparePatch(path, digest);
    }

    public void applyPatch(String path, List<Delta> deltas) throws IOException {
        proxyTarget.applyPatch(path, deltas);
    }

    public void eraseFile(String path) throws IOException {
        proxyTarget.eraseFile(path);
    }

    public void eraseDirectory(String path) throws IOException {
        proxyTarget.eraseDirectory(path);
    }

    private static interface RFileSyncSlave extends FileSyncParty, Remote {
        
    }
    
    private static class RemoteSkeleton implements RFileSyncSlave {
        
        private final FileSyncParty slave;

        public RemoteSkeleton(FileSyncParty slave) {
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

        public OutputStream openFileForWrite(String path) throws IOException {
            return new OutputStreamRemoteAdapter(slave.openFileForWrite(path));
        }
        
        public void streamFile(String path, OutputStream sink) throws IOException {
            slave.streamFile(path, sink);
        }

        public List<Delta> preparePatch(String path, List<ChecksumPair> digest) throws IOException {
            return slave.preparePatch(path, digest);
        }

        public void applyPatch(String path, List<Delta> deltas) throws IOException {
            slave.applyPatch(path, deltas);
        }

        public void eraseFile(String path) throws IOException {
            slave.eraseFile(path);
        }

        public void eraseDirectory(String path) throws IOException {
            slave.eraseDirectory(path);
        }
    }
}
