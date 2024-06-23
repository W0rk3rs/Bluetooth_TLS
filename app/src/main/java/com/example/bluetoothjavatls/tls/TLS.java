package com.example.bluetoothjavatls.tls;

import android.content.Context;
import com.example.bluetoothjavatls.R;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class TLS {
    private final SSLContext sslContext;

    public TLS(Context context) throws Exception {
        // Load Keystore
        KeyStore keyStore = KeyStore.getInstance("BKS");

        try (InputStream keyStoreIS = context.getResources().openRawResource(R.raw.client_keystore)) {
            keyStore.load(keyStoreIS, "client".toCharArray());
        }

        // Load Truststore
        KeyStore trustStore = KeyStore.getInstance("BKS");
        try (InputStream trustStoreStream = context.getResources().openRawResource(R.raw.server_truststore)) {
            trustStore.load(trustStoreStream, "server".toCharArray());
        }

        // Initialize KeyManagerFactory
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "client".toCharArray());

        // Initialize TrustManagerFactory
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        // Initialize SSLContext with a specific TLS version
        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }
}
