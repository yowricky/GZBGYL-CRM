package com.gzbgyl.crm.attachment.application;

import java.io.InputStream;

public record StoredObject(String contentType, long size, InputStream stream) {
}
