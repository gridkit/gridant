package org.gridkit.lab.gridant;

import java.io.InputStream;
import java.io.PrintStream;

import org.apache.tools.ant.DemuxInputStream;
import org.apache.tools.ant.DemuxOutputStream;
import org.apache.tools.ant.Project;

public class OutputStreamHelper {

	public static InputStream stdIn = System.in;
	public static PrintStream stdOut = System.out;
	public static PrintStream stdErr = System.err;
	
	public static void activate(Project project) {
        System.setIn(new DemuxInputStream(project));
        System.setOut(new PrintStream(new DemuxOutputStream(project, false)));
        System.setErr(new PrintStream(new DemuxOutputStream(project, true)));
	}

	public static void restore() {
        System.setIn(stdIn);
        System.setOut(stdOut);
        System.setErr(stdErr);
	}
}
