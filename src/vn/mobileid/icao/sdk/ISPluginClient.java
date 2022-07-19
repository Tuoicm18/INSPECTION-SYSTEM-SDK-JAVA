/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import vn.mobileid.icao.sdk.util.ISPluginException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.mobileid.icao.sdk.message.resp.CardDetectionEventResp;
import vn.mobileid.icao.sdk.message.resp.DeviceDetailsResp;
import vn.mobileid.icao.sdk.message.resp.DocumentDetailsResp;
import vn.mobileid.icao.sdk.message.resp.BiometricAuthResp;
import vn.mobileid.icao.sdk.message.resp.DisplayInformationResp;
import vn.mobileid.icao.sdk.message.resp.ConnectToDeviceResp;
import vn.mobileid.icao.sdk.message.resp.ScanDocumentResp;

/**
 *
 * @author TRUONGNNT
 */
public final class ISPluginClient {

    //<editor-fold defaultstate="collapsed" desc="VARIABLE">
    private static final Logger LOGGER = LoggerFactory.getLogger(ISPluginClient.class);

    private EventLoopGroup wsGroup;
    private final ScheduledExecutorService pingExe = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("ISPC-Monitor-" + count.incrementAndGet());
            return t;
        }
    });
//    private final EventLoopGroup pingExe = new NioEventLoopGroup(new ThreadFactory() {
//        AtomicInteger count = new AtomicInteger();
//
//        @Override
//        public Thread newThread(Runnable r) {
//            Thread t = new Thread(r);
//            t.setName("ISPC-Monitor-" + count.incrementAndGet());
//            return t;
//        }
//    });
    private final AtomicInteger pingCount = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean isConnect = new AtomicBoolean(false);

    private ConnectToDeviceResp deviceConnected;

    private ScheduledFuture fSendPing;
    private Channel ch;

    private static final long PING = 10;
    private static final int MAX_PING = 5;

    private final URI uri;
    private final String host;
    private final int port;
    private final SslContext sslCtx;
    private WebSocketClientHandler handler;

    private ISListener listener;

    private boolean monitor = true;
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="INTERFACE">
    interface DetailsListener {

        void onError(Exception error);
    }

    public interface DeviceDetailsListener extends DetailsListener {

        void onReceivedDeviceDetails(DeviceDetailsResp device);

    }

    public interface DocumentDetailsListener extends DetailsListener {

        void onReceivedDocumentDetails(DocumentDetailsResp document);
    }

    public interface BiometricAuthListener extends DetailsListener {

        void onBiometricAuth(BiometricAuthResp biometricAuth);
    }

    public interface DisplayInformationListener extends DetailsListener {

        void onSuccess();
    }

    public interface ConnectToDeviceListener extends DetailsListener {

        void onConnectToDevice(ConnectToDeviceResp device);
    }

    public interface ScanDocumentListener extends DetailsListener {

        void onScanDocument(ScanDocumentResp resultScanDocument);
    }

    public interface ISListener {

        boolean onReceivedDocument(DocumentDetailsResp document);

        boolean onReceivedBiometricAuth(BiometricAuthResp resultBiometricAuth);

        boolean onReceivedCardDetecionEvent(CardDetectionEventResp resultCardDetectionEvent);

        void onPreConnect();

        void onConnected();

        void onAccepted(ConnectToDeviceResp device);

        void onDisconnected();

        void doSend(CmdType cmd, String id, ISMessage data);

        void onReceive(CmdType cmd, String id, int error, ISMessage data);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="CONSTRUCTOR">
    public ISPluginClient(String ip, int port, boolean isSecure, String dirTrust, ISListener listener) throws Exception {
        this(new URI((isSecure ? "wss" : "ws") + "://" + ip + ":" + port + "/ISPlugin"), dirTrust, listener);
    }

    public ISPluginClient(URI uri) throws ISPluginException {
        this(uri, null, null);
    }

    public ISPluginClient(URI uri, ISListener listener) throws ISPluginException {
        this(uri, null, listener);
    }

    public ISPluginClient(URI uri, String dirTrust, ISListener listener) throws ISPluginException {
        try {
            this.uri = uri;
            this.host = uri.getHost();
            this.port = uri.getPort() == -1 ? 80 : uri.getPort();
            final String scheme = uri.getScheme();
            if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
                throw new ISPluginException("Scheme is invalid, only support wss or ws but got [" + scheme + "]");
            }
            final boolean ssl = "wss".equalsIgnoreCase(scheme);
            if (ssl) {
                this.sslCtx = SslContextBuilder.forClient().trustManager(Utils.createX500TrustManager(dirTrust)).build();
            } else {
                this.sslCtx = null;
            }
            this.listener = listener;

            //connect();         
//            this.fSendPing = this.pingExe.scheduleWithFixedDelay(() -> {
//                if (shutdown.get()) {
//                    LOGGER.debug("Client was shutdown");
//                    return;
//                }
//                if (handler == null || !handler.isOpen()) {
//                    try {
//                        LOGGER.debug("Channel is closed, re-connect ...");
//                        connect(connectDevice);
//                    } catch (ISPluginException ex) {
//                        LOGGER.error("Error when re-connect, caused by", ex);
//                    }
//                } else if (handler.needSendPing(PING) && monitor) {
//                    try {
//                        if (sendPing() > MAX_PING) {
//                            LOGGER.debug("Ping count reachs max, close this connection");
//                            close();
//                            try {
//                                LOGGER.debug("Re-connect ...");
//                                connect(connectDevice);
//                            } catch (ISPluginException ex) {
//                                LOGGER.error("Error when re-connect, caused by", ex);
//                            }
//                        }
//                    } catch (ISPluginException ex) {
//                        LOGGER.error("Error when send ping ", ex);
//                    }
//                }
//            }, 0, PING, TimeUnit.SECONDS);
        } catch (ISPluginException ex) {
            //wsGroup.shutdownGracefully();
            throw ex;
        } catch (IOException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException ex) {
            //wsGroup.shutdownGracefully();
            throw new ISPluginException(ex);
        } finally {

        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HANDLE SOCKET">
    public void setListener(ISListener listener) {
        this.listener = listener;
    }

    public synchronized ConnectToDeviceResp connect(RequireConnectDevice connectDevice, long timeoutMilli) throws ISPluginException {
        if (isConnect.get()) {
            throw new ISPluginException("Client is connecting ...");
        }
        //CountDownLatch wait = new CountDownLatch(1);
        connectToPlugin(connectDevice, timeoutMilli);

        if (monitor) {
            this.fSendPing = this.pingExe.scheduleWithFixedDelay(() -> {
                if (shutdown.get()) {
                    LOGGER.debug("Client was shutdown");
                    return;
                }
                if (handler == null || !handler.isOpen()) {
                    try {
                        LOGGER.debug("Channel is closed, re-connect ...");
                        connectToPlugin(connectDevice, timeoutMilli);
                    } catch (ISPluginException ex) {
                        LOGGER.error("Error when re-connect, caused by", ex);
                    }
                } else if (handler.needSendPing(PING) && monitor) {
                    try {
                        if (sendPing() > MAX_PING) {
                            LOGGER.debug("Ping count reachs max, close this connection");
                            close();
                            try {
                                LOGGER.debug("Re-connect ...");
                                connectToPlugin(connectDevice, timeoutMilli);
                            } catch (ISPluginException ex) {
                                LOGGER.error("Error when re-connect, caused by", ex);
                            }
                        }
                    } catch (ISPluginException ex) {
                        LOGGER.error("Error when send ping ", ex);
                    }
                }
            }, 0, PING, TimeUnit.SECONDS);
        }
        isConnect.set(true);

//        if (connectDevice == null) {
//            return null;
//        }
//        try {
//            wait.await(timeoutMilli, TimeUnit.MILLISECONDS);
//        } catch (Exception ex) {
//            throw new ISPluginException(ex);
//        }
        return this.deviceConnected;
    }

    private void connectToPlugin(RequireConnectDevice connectDevice, long timeout) throws ISPluginException {
        if (listener != null) {
            listener.onPreConnect();
        }

        if (wsGroup == null || wsGroup.isShutdown()) {
            LOGGER.debug("Create new NioEventLoopGroup");
            wsGroup = new NioEventLoopGroup(new ThreadFactory() {
                AtomicInteger count = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("ISPC-WSS-Client-" + count.incrementAndGet());
                    return t;
                }
            });
        }
        //ChannelFuture chFuture = null;
        try {
            handler = new WebSocketClientHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(this.uri, WebSocketVersion.V13,
                            null, true, new DefaultHttpHeaders()), pingCount, listener);
            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            Bootstrap b = new Bootstrap();
            b.group(wsGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    CustomWebSocketClientCompressionHandler.INSTANCE,
                                    handler);
                        }
                    });
            ChannelFuture chFuture = b.connect(host, port);

            this.ch = chFuture.sync().channel();
            this.handler.handshakeFuture().sync();   //wait connected
            LOGGER.debug("Connect to [{}:{}] successful", host, port);
            if (connectDevice == null) {
                return;
            }
            //connectDeviceAsync(prop);
            return;
            //return handler;
        } catch (Exception ex) {
//            if (chFuture != null) {
//                chFuture.channel().close();
//                chFuture.cancel(true);
//            }
            wsGroup.shutdownGracefully();
            throw new ISPluginException(ex);
        } finally {
        }
    }

    @ChannelHandler.Sharable
    private static final class CustomWebSocketClientCompressionHandler extends WebSocketClientExtensionHandler {

        public static final CustomWebSocketClientCompressionHandler INSTANCE = new CustomWebSocketClientCompressionHandler();

        private CustomWebSocketClientCompressionHandler() {
            super(new PerMessageDeflateClientExtensionHandshaker(6, ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(),
                    PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE, true, true),
                    new DeflateFrameClientExtensionHandshaker(false),
                    new DeflateFrameClientExtensionHandshaker(true));
        }
    }

    private void close() {
        try {
            LOGGER.debug("Close wss connection");
            if (this.ch != null && this.ch.isOpen()) {
                this.ch.writeAndFlush(new CloseWebSocketFrame());
                this.ch.closeFuture().sync();
            }
        } catch (Exception ex) {
            LOGGER.error("Error when close wss client, caused by ", ex);
        }
    }

    public void shutdown() throws ISPluginException {
        try {
            this.shutdown.set(true);
            //this.close();
            if (this.fSendPing != null) {
                this.fSendPing.cancel(true);
            }
        } catch (Exception ex) {
            throw new ISPluginException(ex);
        } finally {
            try {
                this.pingExe.shutdownNow();
                //this.pingExe.awaitTermination(60, TimeUnit.SECONDS);
            } catch (Exception ex) {
                LOGGER.error("Error when shutdown ping-exe", ex);
            }
            try {
                this.wsGroup.shutdownGracefully();
            } catch (Exception ex) {
                LOGGER.error("Error when shutdown ws-client-exe", ex);
            }
            if (listener != null) {
                listener.onDisconnected();
            }
            LOGGER.debug("Shutdown wss client");
        }
    }

    private void check() throws ISPluginException {
        if (shutdown.get() || this.ch == null || !this.ch.isOpen()) {
            throw new ISPluginException("Can not send to server caused by Client_is_shutdown | Channel_is_not_open");
        }

    }

    public int sendPing() throws ISPluginException {
        check();
        LOGGER.debug("Send ping");
        if (this.ch.isOpen()) {
            this.ch.writeAndFlush(new PingWebSocketFrame());
            return this.pingCount.incrementAndGet();
        }
        return this.pingCount.get();
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="GET DEVICE DETAILS">
    public DeviceDetailsResp getDeviceDetails(boolean deviceDetailsEnabled, boolean presenceEnabled,
                                              long timeoutMilliSec, int timeOutInterval) throws ISPluginException {
        return getDeviceDetailsAsync(deviceDetailsEnabled, presenceEnabled,timeOutInterval, null)
                .waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<DeviceDetailsResp> getDeviceDetailsAsync(boolean deviceDetailsEnabled, boolean presenceEnabled,
                                                                 int timeOutInterval, DeviceDetailsListener deviceDetailsListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.GetDeviceDetails;
        String reqID = Utils.getUUID();
        ISRequest req = (ISRequest) ISRequest.builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .timeOutInterval(timeOutInterval)
                .data(RequireDeviceDetails.builder()
                        .deviceDetailsEnabled(deviceDetailsEnabled)
                        .presenceEnabled(presenceEnabled)
                        .build())
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync<DeviceDetailsResp> responseSync = ResponseSync.<DeviceDetailsResp>builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .deviceDetailsListener(deviceDetailsListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="GET DOCUMENT DETAILS">
    public DocumentDetailsResp getDocumentDetails(boolean mrzEnabled, boolean imageEnabled,
                                                  boolean dataGroupEnabled, boolean optionalDetailsEnabled,
                                                  String canValue, String challenge,
                                                  boolean caEnabled, boolean taEnabled,
                                                  long timeoutMilliSec, int timeOutInterval) throws ISPluginException {
        return getDocumentDetailsAsync(mrzEnabled, imageEnabled,
                                       dataGroupEnabled, optionalDetailsEnabled,
                                       canValue, challenge,
                                       caEnabled, taEnabled,
                                       timeOutInterval, null).waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<DocumentDetailsResp> getDocumentDetailsAsync(boolean mrzEnabled, boolean imageEnabled,
                                                                     boolean dataGroupEnabled, boolean optionalDetailsEnabled,
                                                                     String canValue, String challenge,
                                                                     boolean caEnabled, boolean taEnabled,
                                                                     int timeOutInterval, DocumentDetailsListener documentDetailsListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.GetInfoDetails;
        String reqID = Utils.getUUID();
        ISRequest<RequireInfoDetails> req = (ISRequest) ISRequest.builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .data(RequireInfoDetails.builder()
                        .mrzEnabled(mrzEnabled)
                        .imageEnabled(imageEnabled)
                        .dataGroupEnabled(dataGroupEnabled)
                        .optionalDetailsEnabled(optionalDetailsEnabled)
                        .canValue(canValue)
                        .challenge(challenge)
                        .caEnabled(caEnabled)
                        .taEnabled(taEnabled)
                        .build())
                .timeOutInterval(timeOutInterval)
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync<DocumentDetailsResp> responseSync = ResponseSync.<DocumentDetailsResp>builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .documentDetailsListener(documentDetailsListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="GET BIOMETRIC AUTH">
    public BiometricAuthResp biometricAuthentication(BiometricType biometricType, Object challengeBiometric,
                                                     ChallengeType challengeType, boolean livenessEnabled,String cardNo, 
                                                     long timeoutMilliSec, int timeOutInterval) throws ISPluginException {
        return biometricAuthenticationAsync(biometricType, challengeBiometric,
                                            challengeType, livenessEnabled,
                                            cardNo, timeOutInterval, null).waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<BiometricAuthResp> biometricAuthenticationAsync(BiometricType biometricType, Object challengeBiometric,
                                                                        ChallengeType challengeType, boolean livenessEnabled, String cardNo,                                                                       
                                                                        int timeOutInterval, BiometricAuthListener biometricAuthListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.BiometricAuthentication;
        String reqID = Utils.getUUID();
        ISRequest<RequireBiometricAuth> req = ISRequest.<RequireBiometricAuth>builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .data(RequireBiometricAuth.builder()
                        .biometricType(biometricType)
                        .cardNo(cardNo)
                        .livenessEnabled(livenessEnabled)
                        .challengeType(challengeType)
                        .challenge(challengeBiometric)
                        .build())
                .timeOutInterval(timeOutInterval)
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync<BiometricAuthResp> responseSync = ResponseSync.<BiometricAuthResp>builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .biometricAuthListener(biometricAuthListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    // </editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="GET CONNECT TO DEVICE">
    public ConnectToDeviceResp connectToDevice(boolean confirmEnabled, String confirmCode,
                                               String clientName, ConfigConnect configConnect,
                                               long timeoutMilliSec,int timeOutInterval) throws ISPluginException {
        return connectToDeviceAsync(confirmEnabled, confirmCode, clientName, configConnect, timeOutInterval, null).waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<ConnectToDeviceResp> connectToDeviceAsync(boolean confirmEnabled, String confirmCode,
                                                                  String clientName, ConfigConnect configConnect,
                                                                  int timeOutInterval, ConnectToDeviceListener connectToDeviceListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.ConnectToDevice;
        String reqID = Utils.getUUID();
        ISRequest<RequireConnectDevice> req = ISRequest.<RequireConnectDevice>builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .timeOutInterval(timeOutInterval)
                .data(RequireConnectDevice.builder()
                    .confirmEnabled(confirmEnabled)
                    .confirmCode(confirmCode)
                    .clientName(clientName)
                    .configuration(configConnect)
                    .build())
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync<ConnectToDeviceResp> responseSync = ResponseSync.<ConnectToDeviceResp>builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .connectToDeviceListener(connectToDeviceListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET DISPLAY INFORMATION">
    public DisplayInformationResp displayInformation(String title, DisplayType type, 
                                                     String value, int timeOutInterval,
                                                     long timeoutMilliSec) throws ISPluginException {
        return displayInformationAsync(title, type, value, timeOutInterval, null).waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<DisplayInformationResp> displayInformationAsync(String title, DisplayType type,
                                                                        String value, int timeOutInterVal,
                                                                        DisplayInformationListener displayInformationListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.DisplayInformation;
        String reqID = Utils.getUUID();
        ISRequest<DisplayInformation> req = ISRequest.<DisplayInformation>builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .data(DisplayInformation.builder()
                        .title(title)
                        .type(type)
                        .value(value)
                        .build())
                .timeOutInterval(timeOutInterVal)
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync responseSync = ResponseSync.builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .displayInformationListener(displayInformationListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="REFRESH READER">
    public DeviceDetailsResp refreshReader(boolean deviceDetailsEnabled, boolean presenceEnabled,
                                           long timeoutMilliSec, int timeOutInterval) throws ISPluginException {
        return refreshReaderAsync(deviceDetailsEnabled, presenceEnabled, timeOutInterval,null)
                .waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<DeviceDetailsResp> refreshReaderAsync(boolean deviceDetailsEnabled, boolean presenceEnabled,
                                                              int timeOutInterval, DeviceDetailsListener deviceDetailsListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.Refresh;
        String reqID = Utils.getUUID();
        ISRequest req = (ISRequest) ISRequest.builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .data(RequireDeviceDetails.builder()
                        .deviceDetailsEnabled(deviceDetailsEnabled)
                        .presenceEnabled(presenceEnabled)
                        .build())
                .timeOutInterval(timeOutInterval)
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync<DeviceDetailsResp> responseSync = ResponseSync.<DeviceDetailsResp>builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .deviceDetailsListener(deviceDetailsListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET SCAN DOCUMENT">
    public ScanDocumentResp scanDocument(ScanType scanType, boolean saveEnabled,
                                         long timeoutMilliSec, int timeOutInterval) throws ISPluginException {
        return scanDocumentSync(scanType, saveEnabled, timeOutInterval, null).waitResponse(timeoutMilliSec, TimeUnit.MILLISECONDS);
    }

    public ResponseSync<ScanDocumentResp> scanDocumentSync(ScanType scanType, boolean saveEnabled,
                                                           int timeOutInterval,ScanDocumentListener scanDocumentListener) throws ISPluginException {
        check();
        CmdType cmdType = CmdType.ScanDocument;
        String reqID = Utils.getUUID();
        ISRequest<RequireScanDocument> req = ISRequest.<RequireScanDocument>builder()
                .cmdType(cmdType)
                .requestID(reqID)
                .timeOutInterval(timeOutInterval)
                .data(RequireScanDocument.builder()
                    .scanType(scanType)
                    .saveEnabled(saveEnabled)
                    .build())
                .build();

        LOGGER.debug(">>> SEND: [" + Utils.GSON.toJson(req) + "]");
        ResponseSync<ScanDocumentResp> responseSync = ResponseSync.<ScanDocumentResp>builder()
                .cmdType(cmdType)
                .wait(new CountDownLatch(1))
                .scanDocumentListener(scanDocumentListener)
                .build();
        handler.request.put(reqID, responseSync);
        if (this.listener != null) {
            this.listener.doSend(cmdType, reqID, req);
        }
        this.ch.writeAndFlush(new TextWebSocketFrame(Utils.GSON.toJson(req)));
        return responseSync;
    }
    //</editor-fold>
}
