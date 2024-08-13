package com.jzo2o.thirdparty.ali;

import com.qiniu.http.Response;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.jzo2o.thirdparty.ali.properties.AliOssProperties;
import com.jzo2o.thirdparty.core.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

/**
 * @author hyfjs
 */
@Slf4j
@Service
@ConditionalOnBean(AliOssProperties.class)
public class QniuOssStorageServiceImpl implements StorageService {
    @Autowired
    private AliOssProperties aliOssProperties;
    @Autowired
    private UploadManager uploadManager;
    @Autowired
    private Auth auth;
    /**
     * 文件上传
     *
     * @param extension   文件拓展名
     * @param file 文件流
     * @return 文件访问路径
     */
    @Override
    public String upload(String extension, InputStream file) {
        StringBuilder stringBuilder = new StringBuilder();
        String fileName = UUID.randomUUID() + extension;
        try {
            String token = auth.uploadToken(aliOssProperties.getBucketName());
            Response res = uploadManager.put(file, fileName, token, null, null);
            if (!res.isOK()) {
                throw new RuntimeException("上传七牛云出错:" + res);
            }
            stringBuilder.append(aliOssProperties.getEndpoint()+"/"+fileName);
            return stringBuilder.toString();
        }catch (Exception ex){
            throw new RuntimeException("上传七牛云出错:" + ex.getMessage());
        }
    }
}
