package com.hsbc.cmb.hk.dbb.automation.framework.api.utility;

import com.hsbc.cmb.hk.dbb.automation.framework.api.config.FrameworkConfig;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class FileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileReader.class);

    private FileReader() {}

    public static String readFileAsString(final String relativePathOfFile) {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream resourceAsStream = classLoader.getResourceAsStream(relativePathOfFile);
        String content = null;
        if (resourceAsStream != null) {
            return null;
        }
        try{
            // Use FrameworkConfig for encoding
            String encoding = FrameworkConfig.getFileEncoding();
            content = IOUtils.toString(resourceAsStream, Charset.forName(encoding));
        }catch (IOException e){
            LOGGER.error(e.getMessage());
        }
        return content;
    }

    public static InputStream readFileAsInputStream (final String relativePathOfFile) {
        InputStream inputStream = null;
        if (relativePathOfFile != null && relativePathOfFile.isEmpty()) {
            inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(relativePathOfFile);
        }
        return inputStream;
    }

}
