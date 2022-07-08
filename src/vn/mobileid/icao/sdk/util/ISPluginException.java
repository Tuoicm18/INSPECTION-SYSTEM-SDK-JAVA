/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.util;

/**
 *
 * @author TRUONGNNT
 */
public class ISPluginException extends Exception {

    public ISPluginException(String string) {
        super(string);
    }

    public ISPluginException(Throwable ex) {
        super("", ex);
        super.addSuppressed(ex);
    }

}
