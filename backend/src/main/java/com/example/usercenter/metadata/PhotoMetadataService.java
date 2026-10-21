package com.example.usercenter.metadata;

import java.io.InputStream;

public interface PhotoMetadataService {
    PhotoMetadata extract(InputStream inputStream);
}
