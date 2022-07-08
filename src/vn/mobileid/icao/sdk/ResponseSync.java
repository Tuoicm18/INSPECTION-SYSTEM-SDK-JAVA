/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import vn.mobileid.icao.sdk.util.ISPluginException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author TRUONGNNT
 * @param <T>
 */
@Builder
@Getter
public class ResponseSync<T> {

    //<editor-fold defaultstate="collapsed" desc="VARIABLES">
    @Builder.Default
    private final CmdType cmdType = null;
    private T response;
    private CountDownLatch wait;
    private Throwable error;

    private ISPluginClient.DeviceDetailsListener deviceDetailsListener;
    private ISPluginClient.DocumentDetailsListener documentDetailsListener;
    private ISPluginClient.BiometricAuthListener biometricAuthListener;
    private ISPluginClient.DisplayInformationListener displayInformationListener;
    private ISPluginClient.ConnectDeviceListener connectDeviceListener;
    private ISPluginClient.ScanDocumentListener scanDocumentListener;
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="CONSTRUCTOR">
    public T waitResponse(long timeout, TimeUnit timeUnit) throws ISPluginException {
        try {
            if (!wait.await(timeout, timeUnit)) {
                throw new ISPluginException("Timeout to receive response");
            }
            if (error != null) {
                throw error;
            }
            return response;
        } catch (ISPluginException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new ISPluginException(ex);
        }
    }
    //</editor-fold>

    public void setError(Throwable error) {
        this.error = error;
        wait.countDown();
    }

    public void setSuccess(T res) {
        this.response = res;
        wait.countDown();
    }
}
