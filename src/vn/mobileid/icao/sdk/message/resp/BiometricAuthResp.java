/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.message.resp;

import lombok.Getter;
import vn.mobileid.icao.sdk.ChallengeBiometricAuth;
import vn.mobileid.icao.sdk.BiometricType;

/**
 *
 * @author TRUONGNNT
 */
@Getter
public class BiometricAuthResp{

    private BiometricType biometricType;
    private boolean result;
    private Float score;
    private String jwt;
    private int issueDetailCode;
    private String issueDetailMessage;
    private String biometricEvidence;
    private ChallengeBiometricAuth challenge;
}
