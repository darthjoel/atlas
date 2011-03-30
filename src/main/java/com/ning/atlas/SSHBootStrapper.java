package com.ning.atlas;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;

public class SSHBootStrapper implements BootStrapper
{
    private final static Logger logger = LoggerFactory.getLogger(SSHBootStrapper.class);

    private final File   privateKeyFile;
    private final String userName;

    public SSHBootStrapper(File privateKeyFile, String userName)
    {

        this.privateKeyFile = privateKeyFile;
        this.userName = userName;
    }

    public void bootStrap(Server s) throws InterruptedException
    {

        boolean success = false;
        while (!success) {
            try {
                executeRemote(s, s.getBootStrap());
                success = true;
            }
            catch (ConnectException e) {
                // sshd not running yet
                Thread.sleep(1000);
            }
            catch (TransportException e) {
                // these happen sometimes when sshd is accepting cons but not yet ready
                Thread.sleep(1000);
            }
            catch (UserAuthException e) {
                // for some reason on EC2 the key isn't available initially. NFC why.
                Thread.sleep(1000);
            }
            catch (IOException e) {
                logger.warn("exception trying to bootstrap", e);
                Thread.sleep(1000);
            }
        }
    }

    public String executeRemote(Server server, String command) throws IOException
    {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(server.getExternalIpAddress());

        PKCS8KeyFile keyfile = new PKCS8KeyFile();
        keyfile.init(privateKeyFile);
        ssh.authPublickey(userName, keyfile);

        Session session = ssh.startSession();
        Session.Command c = session.exec(command);
        try {
            return c.getOutputAsString();
        }
        finally {
            c.close();
            session.close();
            ssh.disconnect();
        }
    }
}
