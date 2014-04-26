Distributed ANT Extention
=========

This extention to Apache Ant allows really simple
remote execution using SSH infrastructure.

Supported tasks
----

#### &lt;cluster&gt; task
Maps logical node IDs to real host details.

    <cluster>
        <basepath>/apps/myapp</basepath>
        <server id="server1" host="cbox1.acme.com"/>
        <server id="server2" host="cbox2.acme.com"/>
    </cluster>
    
For authentication configuration see [Nanocloud SSH configuration details][1].
    
#### &lt;remotely&gt; task
Executes nested tasks on remote node (or nodes) defined by `<cluster>` task.
Wild cards could be used to execute task in parallel accross servers.

    <remotely servers="server*">
        <echo>This is slave ${slave.id}</echo>
    </remotely>
    
#### &lt;syncdown&gt; task
Synchronize file systems from master to slave process. This task can only be executed inside of `<remotely>` task.

    <remotely servers="server*">
        <echo>This is slave ${slave.id}</echo>
        <syncdown sourceBase="target/resources">
            <retain>logs/**</retain> <!-- do not remove content of "logs" directory at destination -->
            <exclude>**/.mkdir</exclude> <!-- do not copy these files -->
            <exclude>**/b.txt</exclude> <!-- do not copy these files -->
            <copy rename="b.txt">**/b.txt.v2</copy> <!-- rename file b.txt.v2 into b.txt while copying -->
            <copy/> <!-- copy rest of context as is -->
        </syncdown>
    </remotely>
    
 - rsync like delta compression protocol is used to reduce data transfer.
 - destination derectory is clean (equivalent to removing and creating fresh copy).
 - optionally certain files could be retained at destination
    

#### &lt;urlget&gt;
Similar fetches resource by URL. May use master process as proxy (usefully if slave is in restricted network).


 [1]: http://code.google.com/p/gridkit/wiki/NanoCloud_Configuring_SSH_credentials
