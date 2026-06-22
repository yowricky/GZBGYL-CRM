package com.gzbgyl.crm.attachment.application;

import java.io.InputStream;

public interface ObjectStorage {
    void put(String key, String contentType, long size, InputStream in);
    StoredObject get(String key);
    void delete(String key);
}
