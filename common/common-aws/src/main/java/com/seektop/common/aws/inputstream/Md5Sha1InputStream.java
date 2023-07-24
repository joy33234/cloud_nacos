package com.seektop.common.aws.inputstream;

import com.amazonaws.internal.SdkFilterInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5Sha1InputStream extends SdkFilterInputStream {

        private static Log log = LogFactory.getLog(com.amazonaws.services.s3.internal.MD5DigestCalculatingInputStream.class);
        private MessageDigest md5Digest;
        private boolean digestCanBeCloned;
        private MessageDigest digestLastMarked;
        private MessageDigest sha1Digest;
        private MessageDigest sha1LastMarked;
        private ByteArrayOutputStream byteArrayOutputStream;
        private Boolean remain;

        public Md5Sha1InputStream(InputStream in) {
            super(in);
            this.resetDigest();
            if (in.markSupported() && !this.digestCanBeCloned) {
                log.debug("Mark-and-reset disabled on MD5 calculation because the digest implementation does not support cloning. This will limit the SDK's ability to retry requests that failed. Consider pre-calculating the MD5 checksum for the request or switching to a security provider that supports message digest cloning.");
            }
        }

    public Md5Sha1InputStream(InputStream in,Boolean remain) {
        super(in);
        this.resetDigest();
        this.remain = remain;
        if (in.markSupported() && !this.digestCanBeCloned) {
            log.debug("Mark-and-reset disabled on MD5 calculation because the digest implementation does not support cloning. This will limit the SDK's ability to retry requests that failed. Consider pre-calculating the MD5 checksum for the request or switching to a security provider that supports message digest cloning.");
        }
    }

        private void resetDigest() {
            try {
                if(remain){
                    byteArrayOutputStream = new ByteArrayOutputStream();
                }
                this.md5Digest = MessageDigest.getInstance("MD5");
                this.sha1Digest = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException var2) {
                throw new IllegalStateException("No message digest support for MD5 was found.", var2);
            }

            this.digestCanBeCloned = this.canBeCloned(this.md5Digest);
        }

        private boolean canBeCloned(MessageDigest digest) {
            try {
                digest.clone();
                return true;
            } catch (CloneNotSupportedException var3) {
                return false;
            }
        }

        private MessageDigest cloneFrom(MessageDigest from) {
            try {
                return (MessageDigest)from.clone();
            } catch (CloneNotSupportedException var3) {
                throw new IllegalStateException("Message digest implementation does not support cloning.", var3);
            }
        }

        public boolean markSupported() {
            return super.markSupported() && this.digestCanBeCloned;
        }

        public byte[] getMd5Digest() {
            return this.md5Digest.digest();
        }

    public byte[] getSHA1Digest() {
        return this.sha1Digest.digest();
    }

        public void mark(int readLimit) {
            if (this.markSupported()) {
                super.mark(readLimit);
                this.digestLastMarked = this.cloneFrom(this.md5Digest);
                this.sha1LastMarked = this.cloneFrom(this.sha1Digest);
            }

        }

        public void reset() throws IOException {
            if (this.markSupported()) {
                super.reset();
                if (this.digestLastMarked == null) {
                    this.resetDigest();
                } else {
                    this.md5Digest = this.cloneFrom(this.digestLastMarked);
                    this.sha1LastMarked = this.cloneFrom(this.sha1Digest);

                }

            } else {
                throw new IOException("mark/reset not supported");
            }
        }

        public int read() throws IOException {
            int ch = super.read();
            if (ch != -1) {
                this.md5Digest.update((byte)ch);
                this.sha1LastMarked.update((byte)ch);
                if (remain){
                    byteArrayOutputStream.write(ch);
                }
            }

            return ch;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            if (result != -1) {
                this.md5Digest.update(b, off, result);
                this.sha1LastMarked.update(b, off, result);
                if (remain){
                    byteArrayOutputStream.write(b, off, len);
                }
            }

            return result;
        }
    }

