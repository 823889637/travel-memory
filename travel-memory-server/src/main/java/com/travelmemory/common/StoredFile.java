package com.travelmemory.common;

public class StoredFile {

    private final String url;
    private final String path;

    public StoredFile(String url, String path) {
        this.url = url;
        this.path = path;
    }

    public String getUrl() {
        return url;
    }

    public String getPath() {
        return path;
    }
}
