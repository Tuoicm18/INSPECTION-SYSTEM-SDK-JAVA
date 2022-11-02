/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.message.resp;

import lombok.Getter;
import vn.mobileid.icao.sdk.BiometricType;
/**
 *
 * @author acer
 */
@Getter
public class BiometricEvidenceResp {
    private BiometricType biometricType;
    private String biometricEvidence;
}
