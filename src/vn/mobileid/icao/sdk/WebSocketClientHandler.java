/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import vn.mobileid.icao.sdk.util.ISPluginException;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.CharsetUtil;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.mobileid.icao.sdk.message.resp.ResultCardDetectionEvent;
import vn.mobileid.icao.sdk.message.resp.DeviceDetails;
import vn.mobileid.icao.sdk.message.resp.DocumentDetails;
import vn.mobileid.icao.sdk.message.resp.ResultBiometricAuth;
import vn.mobileid.icao.sdk.message.resp.ResultConnectDevice;
import vn.mobileid.icao.sdk.message.resp.ResultScanDocument;

/**
 *
 * @author TRUONGNNT
 */
@ChannelHandler.Sharable
class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    //<editor-fold defaultstate="collapsed" desc="VARIABLE">
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClientHandler.class);

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    private final AtomicInteger pingCount;
    private long lastReceived;

    private StringBuffer response;
    private final ISPluginClient.ISListener listener;
    private final ExecutorService executorService;

    final Map<String, ResponseSync> request = new ConcurrentHashMap<>();
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="CONSTRUCTOR">
    public WebSocketClientHandler(WebSocketClientHandshaker handshaker, AtomicInteger pingCount, ISPluginClient.ISListener listener) {
        this.handshaker = handshaker;
        this.pingCount = pingCount;
        this.listener = listener;
        this.executorService = Executors.newFixedThreadPool(2);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HANDLE SOCCKET">
    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        //System.out.println("WebSocket Client disconnected!");
        InetSocketAddress address = ((InetSocketAddress) ctx.channel().remoteAddress());
        LOGGER.debug("WebSocket Client [" + address.getHostName() + ":" + address.getPort() + "] disconnected!");
        if (listener != null) {
            listener.onDisconnected();
        }
        request.forEach((t, u) -> {
            u.setError(new ISPluginException("Cancelled"));
        });
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                InetSocketAddress address = ((InetSocketAddress) ctx.channel().remoteAddress());
                LOGGER.debug("WebSocket Client [" + address.getHostName() + ":" + address.getPort() + "] connected!");
                handshakeFuture.setSuccess();
                pingCount.set(0);
                if (listener != null) {
                    listener.onConnected();
                }
            } catch (WebSocketHandshakeException e) {
                LOGGER.debug("WebSocket Client failed to connect");
                handshakeFuture.setFailure(e);
            }
            return;
        }
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status()
                    + ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            response = new StringBuffer();
            response.append(textFrame.text());
            if (textFrame.isFinalFragment()) {
                processResponse(response.toString());
            } else {
                //LOGGER.debug("<<< REC: " + textFrame.text());
            }
//            do {
//                LOGGER.debug("WebSocket Client received message: " + textFrame.text());
//                response.append(textFrame.text());
//                if (textFrame.isFinalFragment()) {
//                    processResponse(response.toString());
//                    break;
//                } else {
//                    textFrame = textFrame.retain();
//                }
//            } while (!textFrame.isFinalFragment());
        } else if (frame instanceof PongWebSocketFrame) {
            LOGGER.debug("WebSocket Client received pong, ping-count [" + pingCount.decrementAndGet() + "]");
            lastReceived = System.currentTimeMillis();
        } else if (frame instanceof PingWebSocketFrame) {
            LOGGER.debug("WebSocket Client received ping, send pong to server");
            ch.writeAndFlush(new PongWebSocketFrame());
            lastReceived = System.currentTimeMillis();
        } else if (frame instanceof CloseWebSocketFrame) {
            LOGGER.debug("WebSocket Client received closing");
            ch.close();
        } else if (frame instanceof ContinuationWebSocketFrame) {
            ContinuationWebSocketFrame textFrame = (ContinuationWebSocketFrame) frame;
            response.append(textFrame.text());
            if (textFrame.isFinalFragment()) {
                processResponse(response.toString());
            } else {
                //LOGGER.debug("<<< REC: " + textFrame.text());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //cause.printStackTrace();
        LOGGER.error("Error with Channel [" + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress() + "] caused by ", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

    boolean needSendPing(long free) {
        return System.currentTimeMillis() - lastReceived > free * 1000;
    }

    boolean isOpen() {
        return handshakeFuture != null && handshakeFuture.channel().isOpen();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HANDLE RESPONSE SOCKET">
    private void processResponse(String json) {
        try {
            ISResponse resp = Utils.GSON.fromJson(json, ISResponse.class);
            String reqID = resp.getRequestID();
            LOGGER.debug("<<< REC:  RequestID [{}], CmdType [{}], Error [{}]", reqID, resp.getCmdType(), resp.getErrorCode());
            if (this.listener != null) {
                executorService.submit(() -> {
                    this.listener.onReceive(resp.getCmdType(), reqID, resp.getErrorCode(), resp);
                });
            }
            if (request.containsKey(reqID)) {
                ResponseSync sync = request.get(reqID);
                try {
                    if (resp.getCmdType() == null || resp.getCmdType() != sync.getCmdType()) {
                        throw new ISPluginException("CmdType not match expect [" + sync.getCmdType() + "] but get [" + resp.getCmdType() + "]");
                    }
                    if (resp.getErrorCode() != Utils.SUCCESS) {
                        throw new ISPluginException(resp.getErrorCode(), resp.getErrorMessage());
                    }
                    CmdType cmd = resp.getCmdType();
                    switch (cmd) {
                        case Refresh: //Func 2.9
                        case GetDeviceDetails: // Func 2.1
                            DeviceDetails deviceDetails = getDeviceDetails(json);
                            sync.setSuccess(deviceDetails);
                            if (sync.getDeviceDetailsListener() != null) {
                                executorService.submit(() -> {
                                    sync.getDeviceDetailsListener()
                                            .onReceivedDeviceDetails(deviceDetails);
                                });
                            }
                            break;
                        case GetInfoDetails: // Func 2.2
                            DocumentDetails docDetails = getDocumentDetails(json);
                            sync.setSuccess(docDetails);
                            if (sync.getDocumentDetailsListener() != null) {
                                executorService.submit(() -> {
                                    sync.getDocumentDetailsListener()
                                            .onReceivedDocumentDetails(docDetails);
                                });
                            }
                            break;
                        case BiometricAuthentication: // Func 2.4
                            ResultBiometricAuth biometricAuth = getResultBiometricAuth(json);
                            sync.setSuccess(biometricAuth);
                            if (sync.getBiometricAuthListener() != null) {
                                executorService.submit(() -> {
                                    sync.getBiometricAuthListener()
                                            .onBiometricAuth(biometricAuth);
                                });
                            }
                            break;
                        case ConnectToDevice: // Func 2.5
                            ResultConnectDevice resultConnectDevice = getConnectDevice(json);
                            sync.setSuccess(resultConnectDevice);
                            if (sync.getConnectDeviceListener() != null) {
                                executorService.submit(() -> {
                                    sync.getConnectDeviceListener()
                                            .onConnectDevice(resultConnectDevice);
                                });
                            }
                            break;
                        case DisplayInformation: // Func 2.6
                            sync.setSuccess(null);
                            if (sync.getDisplayInformationListener() != null) {
                                executorService.submit(() -> {
                                    sync.getDisplayInformationListener()
                                            .onSuccess();
                                });
                            }
                            break;
                        case ScanDocument: //Func 2.10
                            ResultScanDocument resultScanDoc = getScanDocument(json);
                            sync.setSuccess(resultScanDoc);
                            if (sync.getScanDocumentListener() != null) {
                                executorService.submit(() -> {
                                    sync.getScanDocumentListener()
                                            .onScanDocument(resultScanDoc);
                                });
                            }
                            break;
                    }
                } catch (Exception ex) {
                    sync.setError(ex);
                    if (sync.getDocumentDetailsListener() != null) {
                        executorService.submit(() -> {
                            sync.getDocumentDetailsListener()
                                    .onError(ex);
                        });
                    }
                    if (sync.getDeviceDetailsListener() != null) {
                        executorService.submit(() -> {
                            sync.getDeviceDetailsListener()
                                    .onError(ex);
                        });
                    }
                    if (sync.getBiometricAuthListener() != null) {
                        executorService.submit(() -> {
                            sync.getBiometricAuthListener()
                                    .onError(ex);
                        });
                    }
                    if (sync.getDisplayInformationListener() != null) {
                        executorService.submit(() -> {
                            sync.getDisplayInformationListener()
                                    .onError(ex);
                        });
                    }
                    if (sync.getConnectDeviceListener() != null) {
                        executorService.submit(() -> {
                            sync.getConnectDeviceListener()
                                    .onError(ex);
                        });
                    }
                    if (sync.getScanDocumentListener()!= null) {
                        executorService.submit(() -> {
                            sync.getScanDocumentListener()
                                    .onError(ex);
                        });
                    }
                } finally {
                    request.remove(reqID);
                }
            } else if (CmdType.SendInfoDetails == resp.getCmdType()) { // Func 2.3
                if (this.listener != null) {
                    executorService.submit(() -> {
                        DocumentDetails documentDetails = getDocumentDetails(json);
                        listener.onReceivedDocument(documentDetails);
                    });
                }
            } else if (CmdType.SendBiometricAuthentication == resp.getCmdType()) { // Func 2.7
                if (this.listener != null) {
                    executorService.submit(() -> {
                        ResultBiometricAuth resultBiometricAuth = getResultBiometricAuth(json);
                        listener.onReceivedBiometricAuth(resultBiometricAuth);
                    });
                }
            } else if (CmdType.CardDetectionEvent == resp.getCmdType()) { //Func 2.8
                if (this.listener != null) {
                    executorService.submit(() -> {
                        ResultCardDetectionEvent cardDetectionEvent = getCardDetectionEvent(json);
                        listener.onReceivedCardDetecionEvent(cardDetectionEvent);
                    });
                }
            } else {
                LOGGER.debug("Not found Request with RequestID [{}], skip Response [{}]", reqID, json);
            }
        } catch (Exception ex) {
            LOGGER.error("Skip response [{}], caused by", json, ex);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET DEVICE DETAILS">
    private DeviceDetails getDeviceDetails(String json) {
        Type type = new TypeToken<ISMessage<DeviceDetails>>() {
        }.getType();
        ISMessage<DeviceDetails> device = Utils.GSON.fromJson(json, type);
        DeviceDetails devcDetails = device.getData();
        return devcDetails;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET DOCUMENT DETAILS">
    private DocumentDetails getDocumentDetails(String json) {
        Type type = new TypeToken<ISMessage<DocumentDetails>>() {
        }.getType();
        ISMessage<DocumentDetails> doc = Utils.GSON.fromJson(json, type);
        DocumentDetails docDetails = doc.getData();
//        if (docDetails != null && docDetails.getDataGroup() != null && docDetails.getDataGroup().getDg1() != null) {
//            byte[] dg1 = docDetails.getDataGroup().getDg1();
//            docDetails.setMrz(new String(dg1, 5, dg1.length - 5, CharsetUtil.UTF_8));
//        }
        return docDetails;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET BIOMETRIC AUTH">
    private ResultBiometricAuth getResultBiometricAuth(String json) {
        Type type = new TypeToken<ISMessage<ResultBiometricAuth>>() {
        }.getType();
        ISMessage<ResultBiometricAuth> biometricAuth = Utils.GSON.fromJson(json, type);
        ResultBiometricAuth resultBiometricAuth = biometricAuth.getData();
//        if (docDetails != null && docDetails.getDataGroup() != null && docDetails.getDataGroup().getDg1() != null) {
//            byte[] dg1 = docDetails.getDataGroup().getDg1();
//            docDetails.setMrz(new String(dg1, 5, dg1.length - 5, CharsetUtil.UTF_8));
//        }
        return resultBiometricAuth;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET CONEECT DEVICE">
    private ResultConnectDevice getConnectDevice(String json) {
        Type type = new TypeToken<ISMessage<ResultConnectDevice>>() {
        }.getType();
        ISMessage<ResultConnectDevice> connect = Utils.GSON.fromJson(json, type);
        ResultConnectDevice resultConnectDevice = connect.getData();
        return resultConnectDevice;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET CARD DETECTION">
    private ResultCardDetectionEvent getCardDetectionEvent(String json) {
        Type type = new TypeToken<ISMessage<ResultCardDetectionEvent>>() {
        }.getType();
        ISMessage<ResultCardDetectionEvent> cardEvent = Utils.GSON.fromJson(json, type);
        ResultCardDetectionEvent cardDetectionEvent = cardEvent.getData();
        return cardDetectionEvent;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="GET SCAN DOCUMENT">
    private ResultScanDocument getScanDocument(String json) {
        Type type = new TypeToken<ISMessage<ResultScanDocument>>() {
        }.getType();
        ISMessage<ResultScanDocument> scanDoc = Utils.GSON.fromJson(json, type);
        ResultScanDocument scanDocument = scanDoc.getData();
        return scanDocument;
    }
    //</editor-fold>
}
