/**
 * Copyright (C) 2011 backcountry.com.
 */
package org.opencommercesearch.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.sshtools.j2ssh.SftpClient;
import com.sshtools.j2ssh.SshClient;
import com.sshtools.j2ssh.authentication.AuthenticationProtocolState;
import com.sshtools.j2ssh.authentication.PasswordAuthenticationClient;
import com.sshtools.j2ssh.transport.IgnoreHostKeyVerification;

public class SSHFileUploader implements FileUploader {

    Logger logger = Logger.getLogger(this.getClass());

    private String serverHostName;
    private String path;
    private String username;
    private String password;

    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    public String getServerHostName() {
        return serverHostName;
    }

    public void setServerHostName(String serverHostName) {
        this.serverHostName = serverHostName;
    }
	
    public String getUsername() {
        return username;
    }
	
    public void setUsername(String username) {
        this.username = username;
    }	

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPath() {
        return path;
	}

    public void setPath(String path) {
        this.path = path;
    }   

    protected SshClient sshClientFactory() {
        return new SshClient();
    }

    public boolean uploadFile(String filename, byte[] content) {
        boolean allSuccessful = true;

        if (!uploadFile(getServerHostName(), getPath(), getUsername(), getPassword(), filename, content)) {
            allSuccessful = false;
            logger.error("Failed to deploy the file for the hostname: " + getServerHostName());
        }
        
        return allSuccessful;
    }

    private boolean uploadFile(String hostname, String path, String username, String password, String filename,
            byte[] content) {
        SshClient ssh = sshClientFactory();
        boolean transferComplete = false;
        if (hostname == null || path == null || username == null || password == null || filename == null
                || content == null) {
            logger.error("Parameter missing. Please make sure all parameters are specified and the stream length is not 0");
            return transferComplete;
        }

        path = (path.endsWith("/")) ? path : path.concat("/");

        try {
            PasswordAuthenticationClient pwd = new PasswordAuthenticationClient();

            ssh.connect(hostname, new IgnoreHostKeyVerification());
            pwd.setUsername(username);
            pwd.setPassword(password);

            int result = ssh.authenticate(pwd);
            if (result == AuthenticationProtocolState.FAILED) {
                logger.error("The authentication failed");
            } else if (result == AuthenticationProtocolState.PARTIAL) {
                logger.error("The authentication succeeded but another authentication is required");
            } else if (result == AuthenticationProtocolState.COMPLETE && content != null) {
                logger.debug("The authentication is complete");
                SftpClient sftp = ssh.openSftpClient();
                InputStream is = new ByteArrayInputStream(content);
                logger.debug("Copying file: " + path + filename);
                sftp.put(is, path + filename);
                transferComplete = true;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                ssh.disconnect();
            } catch (Exception ignored) {
            }
        }
        return transferComplete;
    }
}
