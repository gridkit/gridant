package org.gridkit.lab.gridant.jarsync;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.gridkit.lab.gridant.jarsync.jarsync.ChecksumPair;
import org.gridkit.lab.gridant.jarsync.jarsync.Delta;

/**
 * 
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 *
 */
public interface FileSyncSlave {

    public String resolvePath(String path);
    
    public List<String> listDirectories(String path);
    
    public List<String> listFiles(String path);
    
    public List<ChecksumPair> readChecksums(String path) throws IOException;
    
    public boolean makePath(String path) throws IOException;
    
    public OutputStream openFile(String path) throws IOException;
    
    public void patchFile(String path, List<Delta> deltas) throws IOException;
    
    public void eraseFile(String path) throws IOException;

    public void eraseDirectory(String path) throws IOException;    
    
}
