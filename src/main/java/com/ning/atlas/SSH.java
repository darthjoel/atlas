package com.ning.atlas;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class SSH
{
    private final static Logger logger = LoggerFactory.getLogger(SSH.class);
    private final SSHClient ssh;
    private final String host;

    public SSH(File privateKeyFile, String userName, String host) throws IOException
    {
        this(privateKeyFile, userName, host, 2, TimeUnit.MINUTES);
    }

    public SSH(File privateKeyFile, String userName, String host, long time, TimeUnit unit) throws IOException
    {
        long give_up_at = System.currentTimeMillis() + unit.toMillis(time);

        boolean connected = false;
        SSHClient ssh = null;
        while (!connected) {
            if (System.currentTimeMillis() > give_up_at) {
                throw new IOException("gave up trying to connect after too long");
            }
            ssh = new SSHClient();
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            try {
                ssh.connect(host);
                PKCS8KeyFile keyfile = new PKCS8KeyFile();
                keyfile.init(privateKeyFile);
                ssh.authPublickey(userName, keyfile);
                connected = true;
            }
            catch (Exception e) {
                // ec2 is not ready yet, probably
                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        this.host = host;
        this.ssh = ssh;
    }

    public void close() throws IOException
    {
        ssh.disconnect();
    }


    public String exec(String commandFormatString, Object... args) throws IOException
    {
        return exec(format(commandFormatString, args));
    }

    public String exec(String command) throws IOException
    {
        Session s = ssh.startSession();
        try {
            logger.debug("executing '{}' on {}", command, host);
            Session.Command cmd = s.exec(command);
            cmd.join();
            String rs = cmd.getOutputAsString() + "\n" + cmd.getErrorAsString();
            cmd.close();
            return rs;
        }
        finally {
            s.close();
        }
    }

    public void scpUpload(File localFile, String remotePath) throws IOException
    {
        logger.debug(format("uploading %s to %s:%s", localFile.getAbsolutePath(), host, remotePath));
        ssh.newSCPFileTransfer().upload(localFile.getAbsolutePath(), remotePath);
    }
}
