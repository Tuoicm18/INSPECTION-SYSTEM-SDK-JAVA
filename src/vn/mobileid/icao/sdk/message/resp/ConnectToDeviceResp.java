/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.message.resp;

import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author TRUONGNNT
 */
@Getter
@Builder
public class ConnectToDeviceResp {
    private String deviceName;
    private String deviceSN;
    private String deviceIP;
}
