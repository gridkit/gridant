package org.gridkit.lab.gridant.jarsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gridkit.lab.gridant.jarsync.jarsync.ChecksumPair;
import org.gridkit.lab.gridant.jarsync.jarsync.Delta;
import org.gridkit.lab.gridant.jarsync.jarsync.Rdiff;

class SimpleSyncSlave implements FileSyncParty {

    private Rdiff rdiff;
    private File basePath;
    
    public SimpleSyncSlave(String path) {
        this.basePath = new File(path);
        if (basePath.isFile()) {
            throw new IllegalArgumentException("Path [path] is a plain file");
        }
    }

    public SimpleSyncSlave(File basePath) {
        this.basePath = basePath;
        this.rdiff = new Rdiff(); 
    }
    
    protected File resolve(String path) {
        if (path == null || path.equals(".") || path.length() == 0) {
            return basePath;
        }
        String fp = basePath.getPath();
        fp.replace('\\', '/');
        fp = fp + '/' + path;
        return new File(fp);
    }

    @Override
    public String resolvePath(String path) {
        return resolve(path).getPath();
    }

    @Override
    public List<String> listDirectories(String path) {
        File base = resolve(path);
        if (!base.exists() || !base.isDirectory()) {
            return Collections.<String>emptyList();
        }
        List<String> list = new ArrayList<String>();
        File[] files = base.listFiles();
        if (files != null) {
            for(File file: files) {
                if (file.isDirectory()) {
                    list.add(file.getName());
                }
            }
        }
        Collections.sort(list);
        return list;
    }


    @Override
    public List<String> listFiles(String path) {
        File base = resolve(path);
        if (!base.exists() || !base.isDirectory()) {
            return Collections.<String>emptyList();
        }
        List<String> list = new ArrayList<String>();
        File[] files = base.listFiles();
        if (files != null) {
            for(File file: files) {
                if (file.isFile()) {
                    list.add(file.getName());
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    @Override
    public List<ChecksumPair> readChecksums(String path) throws IOException {
        File file = resolve(path);
        if (!file.isFile()) {
            return Collections.<ChecksumPair>emptyList();
        }
        FileInputStream fis = new FileInputStream(file);
        try {
            List<ChecksumPair> digest = rdiff.makeSignatures(fis);
            return digest == null ? Collections.<ChecksumPair>emptyList() : digest;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        finally {
            fis.close();
        }
    }

    @Override
    public boolean makePath(String path) throws IOException {
        File file = resolve(path);
        if (file.isDirectory()) {
            return false;
        }
        file.mkdirs();
        if (file.isDirectory()) {            
            return true;
        }
        else {
            throw new IOException("Cannot create directory: " + file.getPath());
        }
    }

    @Override
    public OutputStream openFileForWrite(String path) throws IOException {
        File file = resolve(path);
        if (file.getParentFile() != null || file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(file);
        return fos;
    }

    @Override
    public void streamFile(String path, OutputStream sink) throws IOException {
        File file = resolve(path);
        FileInputStream fis = new FileInputStream(file);
        StreamHelper.copy(fis, sink);
    }

    @Override
    public List<Delta> preparePatch(String path, List<ChecksumPair> digest) throws IOException {
        File file = resolve(path);
        FileInputStream in = new FileInputStream(file);
        try {
            return rdiff.makeDeltas(digest, in);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        finally {
            in.close();
        }
    }

    @Override
    public void applyPatch(String path, List<Delta> deltas) throws IOException {
        File file = resolve(path);
        if (file.getParentFile() != null || file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = mkTmp(file);
        FileOutputStream rout = new FileOutputStream(tmpFile);
        rdiff.rebuildFile(file, deltas, rout);
        rout.close();
        file.delete();
        tmpFile.renameTo(file);
    }

    private File mkTmp(File file) {
        File dir = file.getParentFile();
        if (dir == null) {
            dir = new File(".");
        }
        int n = 0;
        while(true) {
            File f = new File(dir, ".jarsync" + (n == 0 ? "" : "-" + n) + file.getName());
            if (!f.exists()) {
                return f;
            }
            ++n;
        }
    }

    @Override
    public void eraseFile(String path) throws IOException {
        File file = resolve(path);
        file.delete();
        if (file.exists()) {
            throw new IOException("Cannot delete file: " + file.getAbsolutePath());
        }
    }

    @Override
    public void eraseDirectory(String path) throws IOException {
        remove(resolve(path));        
    }
    
    private static void remove(File file) throws IOException {
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for(File l: list) {
                    remove(l);
                }
            }
            file.delete();
        }
        else {
            if (file.exists()) {
                file.delete();
            }
        }           
        if (file.exists()) {
            throw new IOException("Cannot delete: " + file.getPath());
        }
    }    
}
