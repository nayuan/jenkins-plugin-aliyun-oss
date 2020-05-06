package com.codeyyy.jenkins.plugin.aliyun.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

public class AliyunOssUploaderBuilder extends Builder implements SimpleBuildStep {

    private String credentialsId;
    private String bucketName;
    private String localPath;
    private String remotePath;
    private String excludeRegex;
    private Boolean skipExist;

    @DataBoundConstructor
    public AliyunOssUploaderBuilder(
            String credentialsId,
            String bucketName,
            String localPath,
            String remotePath,
            String excludeRegex,
            Boolean skipExist
    ) {
        this.credentialsId = credentialsId;
        this.bucketName = bucketName;
        this.localPath = localPath;
        this.remotePath = remotePath;
        this.excludeRegex = excludeRegex;
        this.skipExist = skipExist;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();
        //check
        if(localPath.startsWith("/")) {
            localPath = localPath.substring(1);
        }
        if(localPath.endsWith("/")) {
            localPath = localPath.substring(0, localPath.length() - 1);
        }
        if(remotePath.startsWith("/")) {
            remotePath = remotePath.substring(1);
        }
        if(remotePath.endsWith("/")) {
            remotePath = remotePath.substring(0, remotePath.length() - 1);
        }

        logger.println(credentialsId);
        logger.println(remotePath);

        FilePath dir = new FilePath(workspace, localPath);

//        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret.getPlainText());
        OSS client = null;
        try{
            if(dir.isDirectory()) {
                scan(client, "", dir, logger);
            }else{
                update(client, "", dir, logger);
            }
        }finally {
            client.shutdown();
        }

    }

    public void scan(OSS client, String path, FilePath dir, PrintStream logger) throws InterruptedException, IOException {
        for(FilePath item : dir.list()) {
            if(item.isDirectory()) {
                scan(client, path + "/" + item.getName(), item, logger);
            }else{
                update(client, path, item, logger);
            }
        }
    }

    public void update(OSS client, String path, FilePath file, PrintStream logger) throws InterruptedException, IOException {
        if(!file.exists()) {
            logger.println(String.format("file [%s] not exists, skipped.", file.getRemote()));
            return;
        }
        if(excludeRegex != null && file.getRemote().matches(excludeRegex)) {
            logger.println(String.format("file [%s] exclude, skipped.", file.getRemote()));
            return;
        }

        String key = String.format("%s%s/%s", remotePath, path, file.getName());
        if(key.startsWith("/")) key = key.substring(1);
        if(skipExist) {
            boolean found = client.doesObjectExist(bucketName, key);
            if(found) {
                logger.println(String.format("file [%s] in oss is exist [%s], skipped.", file.getRemote(), key));
                return;
            }
        }

        logger.println(String.format("uploading [%s] to [%s]", file.getRemote(), key));
        client.putObject(bucketName, key, file.read());
    }

    @Symbol("aliyunOssUploader")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.AliyunOssUploaderBuilder_DescriptorImpl_DisplayName();
        }

    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public String getExcludeRegex() {
        return excludeRegex;
    }

    public Boolean getSkipExist() {
        return skipExist;
    }
}
