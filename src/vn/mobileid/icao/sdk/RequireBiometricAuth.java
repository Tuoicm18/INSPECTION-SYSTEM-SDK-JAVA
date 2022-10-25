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
public class RequireBiometricAuth {
    private BiometricType biometricType;
    private String cardNo;
    private boolean livenessEnabled;
    private boolean biometricEvidenceEnabled;
    private ChallengeType challengeType;
    private Object challenge;
}
