/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author TRUONGNNT
 */
class Utils {

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    public static final int SUCCESS = 0;
    public static final int CONNECT_DENIED = 1008;
    public static final int READ_TIMEOUT = 60;

    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            //.registerTypeHierarchyAdapter(byte[][].class, new ByteArray2DimensionsToBase64TypeAdapter())
            //.registerTypeHierarchyAdapter(boolean.class, new IntToBooleanTypeAdapter())
            //.registerTypeHierarchyAdapter(Class<T>.class, CustomDeserializer<T>)
            //.disableInnerClassSerialization()
            //.serializeNulls()
            //.registerTypeAdapterFactory(new ReflectiveTypeAdapterFactory(constructorConstructor, fieldNamingPolicy, Excluder.DEFAULT, jsonAdapterFactory))
            //.setPrettyPrinting()
            .create();

    static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Base64.getMimeDecoder().decode(json.getAsString());  //Base64.decode(json.getAsString(), Base64.NO_WRAP);
        }

        @Override
        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(src)); //JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP));
        }
    }

    public static TrustManagerFactory createX500TrustManager(String path) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, FileNotFoundException, IOException, CertificateException {
        if (path == null || path.isEmpty()) {
            return wrapX500TrustManager();
        }
        return createX500TrustManager(Paths.get(path));
    }

    public static TrustManagerFactory createX500TrustManager(Path path) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, FileNotFoundException, IOException, CertificateException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return wrapX500TrustManager();
        }
        File f = path.toFile();
        if (f.isFile()) {
            return wrapX500TrustManager(f);
        } else if (f.isDirectory()) {
            List<File> files = new ArrayList<>();
            for (File file : f.listFiles()) {
                if (file.isFile()) {
                    LOGGER.debug("Truststore file: " + file.getAbsolutePath());
                    files.add(file);
                }
            }
            return wrapX500TrustManager(files.toArray(new File[0]));
        } else {
            return wrapX500TrustManager();
        }
    }

    public static TrustManagerFactory wrapX500TrustManager(File... trusted) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, FileNotFoundException, IOException, CertificateException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // Using null here initialises the TMF with the default trust store.
        tmf.init((KeyStore) null);
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        // Get hold of the default trust manager
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                X509Certificate[] defauls = ((X509TrustManager) tm).getAcceptedIssuers();
                if (defauls != null) {
                    for (X509Certificate x509 : defauls) {
                        trustStore.setCertificateEntry(x509.getSerialNumber().toString(), x509);
                    }
                }
                LOGGER.debug("Found X509TrustManager: " + tm.toString());
                break;
            }
        }
        if (trusted == null) {
            return tmf;
        }
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        for (File f : trusted) {
            if (f == null) {
                continue;
            }
            try (final FileInputStream inputStream = new FileInputStream(f)) {
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(inputStream);
                //trustStore.setCertificateEntry("MyTrust" + i, cert);
                trustStore.setCertificateEntry(cert.getSerialNumber().toString(), cert);
            }
        }
        tmf.init(trustStore);

        return tmf;
    }

    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    public static String base64Encode(String s) {
//        LOG.debug("Input: " + s);
        return base64Encode(s.getBytes());
    }

    public static String base64Encode(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    public static byte[] base64Decode(String s) {
        return Base64.getMimeDecoder().decode(s);
    }

//    public static byte[] base64Decode(byte[] b) {
//        return Base64.getMimeDecoder().decode(b);
//    }
}
