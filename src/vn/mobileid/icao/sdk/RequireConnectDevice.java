/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
/**
 *
 * @author acer
 */
@Getter
@Setter
@Builder
public class RequireConnectDevice {
    private boolean confirmEnabled;
    private String confirmCode;
    private String clientName;
    private ConfigConnect configuration;
}
