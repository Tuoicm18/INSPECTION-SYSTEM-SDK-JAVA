/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author TRUONGNNT
 */
@Builder
@Getter
public class ConnectProp {

    private boolean confirmEnabled;
    private String confirmCode;
    private String clientName;
    private ConfigConnect configuration;

    public static class ConfigConnect {

        private boolean mrzEnabled;
        private boolean imageEnabled;
        private boolean dataGroupEnabled;
        private boolean optionalDetailsEnabled;
    }
}
