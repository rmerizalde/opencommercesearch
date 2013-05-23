package org.opencommercesearch.service;

public interface FileUploader {

    public boolean uploadFile(String filename, byte[] content);
    
}
