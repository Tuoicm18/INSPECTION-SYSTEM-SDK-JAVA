/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.util;

import lombok.Setter;
import lombok.Getter;
/**
 *
 * @author TRUONGNNT
 */
@Getter
@Setter
public class ISPluginException extends Exception {
    private int errCode;
    private String errMsg;
    
    public ISPluginException(int errCode, String errMsgInput) {
        super(errMsgInput);
        this.errCode = errCode;
        this.errMsg = errMsgInput;
    }
    
    public ISPluginException(String string) {
        super(string);
        this.errMsg = string;
    }

    public ISPluginException(Throwable ex) {
        super("", ex);
        super.addSuppressed(ex);
    }

}
