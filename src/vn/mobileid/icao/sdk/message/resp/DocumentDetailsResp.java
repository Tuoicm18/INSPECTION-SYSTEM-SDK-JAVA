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
public class DocumentDetailsResp {

    private boolean paceEnabled;
    private boolean bacEnabled;
    private boolean activeAuthenticationEnabled;
    private boolean chipAuthenticationEnabled;
    private boolean terminalAuthenticationEnabled;
    private boolean passiveAuthenticationEnabled;
    private String efCom;
    private String efSod;
    private String efCardAccess;

    private DataGroup dataGroup;
    private OptionalDetails optionalDetails;

    private String mrz;
    private String image;
    private String jwt;
}
